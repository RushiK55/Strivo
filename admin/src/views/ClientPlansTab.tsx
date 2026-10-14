'use client';
// A client's plans: what the trainer sent them (and whether it reached the app), and the plans in their app.
// Sending a plan is the one thing a trainer writes for a client; it lands in users/{uid}/assigned and the app adds it.
import { Send, Undo2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { Empty, ErrorBox, PageSkeleton, ToneBadge } from '@/components/ui';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { dayLabel, describeAssigned } from '@/lib/assign';
import { listAssignments, listLibrary, listUserRows, sendAssignment, withdrawAssignment } from '@/lib/data';
import { useLoad } from '@/lib/hooks';
import { relativeTime } from '@/lib/logic';
import type { Assignment, AssignmentFields, ExerciseRow, LibraryExercise, PlanRow } from '@/lib/types';
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
  const [library, setLibrary] = useState<LibraryExercise[] | null>(null);
  const [open, setOpen] = useState(false);

  async function openForm() {
    setOpen(true);
    // The library is only needed for the form; a library that cannot be read just leaves its picker out.
    if (library === null) setLibrary(await listLibrary().catch(() => []));
  }

  async function send(value: AssignmentFields): Promise<string | undefined> {
    try {
      await sendAssignment(uid, value, admin.uid);
    } catch (error) {
      return `Could not send it: ${error instanceof Error ? error.message : String(error)}`;
    }
    rememberName(value.sentByName);
    setOpen(false);
    toast(`Sent "${value.planName}". It appears in their app the next time it syncs.`);
    sent.reload();
    return undefined;
  }

  async function withdraw(assignment: Assignment) {
    const ok = await confirm({ title: 'Withdraw this plan?', message: `"${assignment.planName}" will not be added to their app.`, confirmText: 'Withdraw' });
    if (!ok) return;
    try {
      await withdrawAssignment(uid, assignment.id);
      toast('Withdrawn.');
    } catch {
      // Most likely the app picked it up a moment ago: it is the client's plan now.
      toast('Could not withdraw it. It may have reached their app already.', 'error');
    }
    sent.reload();
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Plans you sent</CardTitle>
          <CardDescription>A plan you send is added to the client&apos;s app, on the day you pick, the next time the app syncs.</CardDescription>
          <CardAction>
            <Button onClick={() => void openForm()}><Send /> Send a plan</Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          <SentList sent={sent.data} error={sent.error} onWithdraw={(a) => void withdraw(a)} />
        </CardContent>
      </Card>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">In their app</h2>
        {plans.error ? <ErrorBox error={plans.error} /> : plans.data === undefined ? <PageSkeleton /> : <PlanCards plans={plans.data} />}
      </section>

      {open ? (
        <Modal title="Send a plan" onClose={() => setOpen(false)}>
          {library === null ? <PageSkeleton /> : (
            <AssignPlanForm library={library} clientPlans={plans.data ?? []} initialName={rememberedName(admin.email)} onSend={send} onCancel={() => setOpen(false)} />
          )}
        </Modal>
      ) : null}
    </div>
  );
}

function SentList({ sent, error, onWithdraw }: { sent: Assignment[] | undefined; error: unknown; onWithdraw: (assignment: Assignment) => void }) {
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
              {assignment.status === 'delivered'
                ? <ToneBadge tone="good">{`In their app · ${relativeTime(assignment.deliveredAt)}`}</ToneBadge>
                : <ToneBadge tone="warn">Waiting for their app</ToneBadge>}
            </div>
            <p className="text-sm text-muted-foreground">
              {`${assignment.exercises?.length ?? 0} exercises · sent ${relativeTime(assignment.sentAt)}${assignment.sentByName ? ` by ${assignment.sentByName}` : ''}`}
            </p>
            {assignment.exercises?.length ? (
              <p className="text-sm">{assignment.exercises.map((e) => `${e.name} ${describeAssigned(e)}`).join(' · ')}</p>
            ) : null}
            {assignment.note ? <p className="text-sm text-muted-foreground">{`Note: ${assignment.note}`}</p> : null}
          </div>
          {assignment.status === 'sent' ? (
            <Button variant="outline" size="sm" onClick={() => onWithdraw(assignment)}><Undo2 /> Withdraw</Button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

function PlanCards({ plans }: { plans: ClientPlan[] }) {
  if (!plans.length) return <Empty>No plans in their app yet.</Empty>;
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {plans.map(({ plan, exercises }) => (
        <Card key={plan.id}>
          <CardHeader>
            <CardTitle className="flex flex-wrap items-center gap-2 text-base">
              {plan.planName} <ToneBadge tone="info">{plan.planDay === 'Prebuilt' ? 'Library' : plan.planDay ?? ''}</ToneBadge>
            </CardTitle>
            <CardDescription>{`${exercises.length} ${exercises.length === 1 ? 'exercise' : 'exercises'}`}</CardDescription>
          </CardHeader>
          <CardContent>
            {exercises.length ? (
              <ul className="divide-y">
                {exercises.map((exercise) => (
                  <li key={exercise.id} className="py-1.5">
                    <strong>{exercise.name}</strong>
                    <span className="text-muted-foreground">
                      {` ${exercise.sets} × ${exercise.reps}${Number(exercise.dropSets) > 0 ? ` + ${exercise.dropSets} drop` : ''}${Number(exercise.weight) > 0 ? ` @ ${exercise.weight} kg` : ', bodyweight'}`}
                    </span>
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
