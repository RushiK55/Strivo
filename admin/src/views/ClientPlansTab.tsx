'use client';
// A client's plans: what the trainer sent them (whether it reached the app, and what they changed once they accepted it), and the
// plans in their app. Sending a plan is the one thing a trainer writes for a client; it lands in users/{uid}/assigned and the app
// offers it. An accepted plan becomes one of the client's own plans, and what they change on a day is kept in it, set by set.
import { Send, Undo2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { Empty, ErrorBox, PageSkeleton, ToneBadge } from '@/components/ui';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { changesToSentPlan, dayLabel, describeAssigned, describePlanExercise } from '@/lib/assign';
import { listAssignments, listLibrary, listPlanLibrary, listUserRows, sendAssignment, withdrawAssignment } from '@/lib/data';
import { useLoad } from '@/lib/hooks';
import { relativeTime } from '@/lib/logic';
import type { Assignment, AssignmentFields, ExerciseRow, LibraryExercise, LibraryPlan, PlanRow } from '@/lib/types';
import { AssignPlanForm, type ClientPlan } from './AssignPlanForm';

const DAY_ORDER = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday', 'Prebuilt'];
const NAME_KEY = 'strivo.trainerName';

/** The name the trainer last signed a plan with (remembered in this browser only), else the start of their email. */
function rememberedName(email: string): string {
  const fallback = email.split('@')[0] ?? '';
  try { return localStorage.getItem(NAME_KEY) ?? fallback; } catch { return fallback; }
}
function rememberName(name: string) {
  try { localStorage.setItem(NAME_KEY, name); } catch { /* not kept; it is only a convenience */ }
}

/** Plans with their exercises in order, by weekday then name. Demo plans are left out. */
function clientPlans(plans: PlanRow[], exercises: ExerciseRow[]): ClientPlan[] {
  const byPlan = new Map<string, ExerciseRow[]>();
  for (const exercise of exercises) {
    const key = String(exercise.planId);
    byPlan.set(key, [...(byPlan.get(key) ?? []), exercise]);
  }
  return plans
    .filter((plan) => !plan.isDemo)
    .sort((a, b) => DAY_ORDER.indexOf(a.planDay ?? '') - DAY_ORDER.indexOf(b.planDay ?? '') || String(a.planName).localeCompare(String(b.planName)))
    .map((plan) => ({ plan, exercises: [...(byPlan.get(String(plan.planId)) ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)) }));
}

export function ClientPlansTab({ uid }: { uid: string }) {
  const admin = useAdmin();
  const { toast, confirm } = useFeedback();
  const loadPlans = useCallback(async () => {
    const [plans, exercises] = await Promise.all([
      listUserRows<PlanRow>(uid, 'plans', { max: 200 }),
      listUserRows<ExerciseRow>(uid, 'exercises', { max: 1000 }),
    ]);
    return clientPlans(plans, exercises);
  }, [uid]);
  const loadSent = useCallback(() => listAssignments(uid), [uid]);
  const plans = useLoad(loadPlans);
  const sent = useLoad(loadSent);
  const [library, setLibrary] = useState<{ exercises: LibraryExercise[]; plans: LibraryPlan[] } | null>(null);
  const [open, setOpen] = useState(false);

  async function openForm() {
    setOpen(true);
    // The libraries are only needed for the form; one that cannot be read just leaves its picker out.
    if (library === null) {
      const [exercises, plans] = await Promise.all([listLibrary().catch(() => []), listPlanLibrary().catch(() => [])]);
      setLibrary({ exercises, plans });
    }
  }

  async function send(value: AssignmentFields): Promise<string | undefined> {
    try {
      await sendAssignment(uid, value, admin.uid);
    } catch (error) {
      return `Could not send it: ${error instanceof Error ? error.message : String(error)}`;
    }
    rememberName(value.sentByName);
    setOpen(false);
    toast(`Sent "${value.planName}". It reaches their app the next time it syncs, and they choose whether to accept it.`);
    sent.reload();
    return undefined;
  }

  async function withdraw(assignment: Assignment) {
    const ok = await confirm({ title: 'Withdraw this plan?', message: `"${assignment.planName}" will be taken back and never offered to them (or will disappear from their offer).`, confirmText: 'Withdraw' });
    if (!ok) return;
    try {
      await withdrawAssignment(uid, assignment.id);
      toast('Withdrawn.');
    } catch {
      // Most likely the client answered it a moment ago: an answered plan stays.
      toast('Could not withdraw it. They may have answered it already.', 'error');
    }
    sent.reload();
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Plans you sent</CardTitle>
          <CardDescription>A plan you send reaches the client&apos;s app the next time it syncs. They accept or decline it there; accepting puts it on the day you pick. Plans you send close together (a week&apos;s worth) are one offer to them.</CardDescription>
          <CardAction>
            <Button onClick={() => void openForm()}><Send /> Send a plan</Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          <SentList sent={sent.data} error={sent.error} plans={plans.data ?? []} onWithdraw={(a) => void withdraw(a)} />
        </CardContent>
      </Card>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">In their app</h2>
        {plans.error ? <ErrorBox error={plans.error} /> : plans.data === undefined ? <PageSkeleton /> : <PlanCards plans={plans.data} />}
      </section>

      {open ? (
        <Modal title="Send a plan" onClose={() => setOpen(false)}>
          {library === null ? <PageSkeleton /> : (
            <AssignPlanForm library={library.exercises} planLibrary={library.plans} clientPlans={plans.data ?? []} initialName={rememberedName(admin.email)} onSend={send} onCancel={() => setOpen(false)} />
          )}
        </Modal>
      ) : null}
    </div>
  );
}

/** What the client changed in an accepted plan, from their copy of it in their app (the plan whose origin is this assignment). */
function changesOf(assignment: Assignment, plans: ClientPlan[]): string[] | null {
  if (assignment.status !== 'accepted') return null;
  const own = plans.find(({ plan }) => plan.planDay === 'Prebuilt' && plan.origin === `trainer:${assignment.id}`);
  return own ? changesToSentPlan(assignment.exercises ?? [], own.exercises) : null;
}

function SentList({ sent, error, plans, onWithdraw }: { sent: Assignment[] | undefined; error: unknown; plans: ClientPlan[]; onWithdraw: (assignment: Assignment) => void }) {
  if (error) {
    return (
      <Alert>
        <AlertDescription>Could not read the plans you sent. If you just updated the dashboard, publish the new firestore.rules first (see the README).</AlertDescription>
      </Alert>
    );
  }
  if (sent === undefined) return <PageSkeleton />;
  if (!sent.length) return <p className="text-muted-foreground">Nothing sent yet.</p>;
  return (
    <ul className="divide-y">
      {sent.map((assignment) => (
        <li key={assignment.id} className="flex flex-wrap items-start gap-3 py-3">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <strong>{assignment.planName}</strong>
              <ToneBadge tone="info">{dayLabel(assignment.day)}</ToneBadge>
              <StatusBadge assignment={assignment} />
            </div>
            <p className="text-sm text-muted-foreground">
              {`${assignment.exercises?.length ?? 0} exercises · sent ${relativeTime(assignment.sentAt)}${assignment.sentByName ? ` by ${assignment.sentByName}` : ''}`}
            </p>
            {assignment.exercises?.length ? (
              <p className="text-sm">{assignment.exercises.map((e) => `${e.name} ${describeAssigned(e)}`).join(' · ')}</p>
            ) : null}
            {assignment.note ? <p className="text-sm text-muted-foreground">{`Note: ${assignment.note}`}</p> : null}
            <Changes changes={changesOf(assignment, plans)} />
          </div>
          {assignment.status === 'sent' || assignment.status === 'delivered' ? (
            <Button variant="outline" size="sm" onClick={() => onWithdraw(assignment)}><Undo2 /> Withdraw</Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

/** What the client changed in a plan after accepting it: nothing, or a line for each exercise they changed. Null when their copy cannot be found yet. */
function Changes({ changes }: { changes: string[] | null }) {
  if (changes === null) return null;
  if (!changes.length) return <p className="text-sm text-muted-foreground">Their copy is as you sent it.</p>;
  return (
    <div className="mt-1 text-sm">
      <strong>They changed:</strong>
      <ul className="list-disc pl-5">{changes.map((change) => <li key={change}>{change}</li>)}</ul>
    </div>
  );
}

/** Where a plan you sent has got to: not on their phone yet, on it and waiting for an answer, accepted (it is in their plans) or declined. */
function StatusBadge({ assignment }: { assignment: Assignment }) {
  switch (assignment.status) {
    case 'accepted': return <ToneBadge tone="good">{`Accepted · ${relativeTime(assignment.answeredAt)}`}</ToneBadge>;
    case 'declined': return <ToneBadge tone="bad">{`Declined · ${relativeTime(assignment.answeredAt)}`}</ToneBadge>;
    case 'delivered': return <ToneBadge tone="info">{`On their phone, waiting for an answer · ${relativeTime(assignment.deliveredAt)}`}</ToneBadge>;
    default: return <ToneBadge tone="warn">Waiting for their app</ToneBadge>;
  }
}

/** Where one of the client's plans came from, in words: a plan from the plan library, one sent from here, one they made, or the day it was put on. */
function whereFrom(plan: PlanRow): string {
  if (plan.planDay !== 'Prebuilt') return plan.sourcePlanId != null ? 'Kept in their plan' : '';
  if (plan.origin?.startsWith('trainer:')) return 'Sent by a trainer';
  if (plan.origin?.startsWith('library:')) return 'From the plan library';
  return 'Made by them';
}

function PlanCards({ plans }: { plans: ClientPlan[] }) {
  if (!plans.length) return <Empty>No plans in their app yet.</Empty>;
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {plans.map(({ plan, exercises }) => (
        <Card key={plan.id}>
          <CardHeader>
            <CardTitle className="flex flex-wrap items-center gap-2 text-base">
              {plan.planName} <ToneBadge tone="info">{plan.planDay === 'Prebuilt' ? 'Their plan' : plan.planDay ?? ''}</ToneBadge>
            </CardTitle>
            <CardDescription>{[`${exercises.length} ${exercises.length === 1 ? 'exercise' : 'exercises'}`, whereFrom(plan)].filter(Boolean).join(' · ')}</CardDescription>
          </CardHeader>
          <CardContent>
            {exercises.length ? (
              <ul className="divide-y">
                {exercises.map((exercise) => (
                  <li key={exercise.id} className="py-1.5">
                    <strong>{exercise.name}</strong>
                    <span className="text-muted-foreground">{` ${describePlanExercise(exercise)}`}</span>
                    {exercise.notes ? <div className="text-muted-foreground">{exercise.notes}</div> : null}
                  </li>
                ))}
              </ul>
            ) : <p className="text-muted-foreground">No exercises.</p>}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
