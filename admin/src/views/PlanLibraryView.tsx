'use client';
// The plan library: plans built from the exercise library, which every user can browse in the app and put on a day. They work with no
// trainer at all; a user who takes one gets their own copy, so nothing they change ever touches the plan here. Admins add, edit, hide
// and remove plans here, and can send any of them to a client from the client's page.
import { Eye, EyeOff, ListChecks, MoreHorizontal, Pencil, Plus, Trash2, Upload } from 'lucide-react';
import { useState } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, Stat, ToneBadge, type FilterOption } from '@/components/ui';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Switch } from '@/components/ui/switch';
import { deletePlanLibraryPlan, importPlanLibrary, listLibrary, listPlanLibrary, savePlanLibraryPlan, setPlanLibraryActive } from '@/lib/data';
import { useLoad } from '@/lib/hooks';
import { missingFromLibrary, slugify } from '@/lib/logic';
import { describeAssigned } from '@/lib/assign';
import { draftFromPlan, exerciseCount, planGroups, unknownExercises } from '@/lib/planLibrary';
import type { LibraryPlan, LibraryPlanFields } from '@/lib/types';
import { cn } from '@/lib/utils';
import { PlanLibraryForm } from './PlanLibraryForm';

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

/** The starter plans that ship with the dashboard (also the app's first plan library). */
const loadStarter = async () => (await import('../../seed/plan_library.json')).default as unknown as (LibraryPlanFields & { id: string })[];

/** `null` is the dialog for a new plan, a plan is the dialog to edit it, `undefined` is no dialog. */
type Editing = LibraryPlan | null | undefined;

type Status = 'all' | 'active' | 'hidden';

export function PlanLibraryView() {
  const admin = useAdmin();
  const { toast, confirm } = useFeedback();
  const { data: plans, error, reload } = useLoad(listPlanLibrary);
  // The exercise library is what plans are built from; a library that cannot be read just leaves its picker out.
  const exercises = useLoad(listLibrary).data ?? [];
  const [editing, setEditing] = useState<Editing>(undefined);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<Status>('all');

  const all = plans ?? [];
  const visible = all.filter((plan) => plan.active).length;
  const shown = all
    .filter((plan) => (status === 'all' || (status === 'active') === plan.active)
      && (!query.trim() || [plan.name, plan.description, plan.level].some((text) => (text ?? '').toLowerCase().includes(query.trim().toLowerCase()))))
    .sort((a, b) => a.name.localeCompare(b.name));

  const statusOptions: FilterOption<Status>[] = [
    { value: 'all', label: 'All', count: all.length },
    { value: 'active', label: 'Shown', count: visible },
    { value: 'hidden', label: 'Hidden', count: all.length - visible },
  ];

  async function toggle(plan: LibraryPlan) {
    try {
      await setPlanLibraryActive(plan.id, !plan.active, admin.uid);
      toast(plan.active ? `${plan.name} is now hidden from users` : `${plan.name} is now shown to users`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function remove(plan: LibraryPlan) {
    const ok = await confirm({
      title: 'Delete plan',
      message: `Delete "${plan.name}" from the plan library? Users who already took it keep their own copy.`,
      confirmText: 'Delete',
    });
    if (!ok) return;
    try {
      await deletePlanLibraryPlan(plan.id);
      toast(`${plan.name} deleted`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function importStarter() {
    try {
      const seed = await loadStarter();
      const fresh = missingFromLibrary(seed, all.map((plan) => plan.id));
      if (!fresh.length) { toast('The starter plans are already imported.'); return; }
      const ok = await confirm({
        title: 'Import starter plans',
        message: `Add ${fresh.length} starter plans (Push Day, Pull Day, Leg Day and more)? Plans already in the library are not changed.`,
        confirmText: 'Import',
      });
      if (!ok) return;
      const written = await importPlanLibrary(fresh, admin.uid);
      toast(`${written} plans imported`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function save(value: LibraryPlanFields): Promise<string | undefined> {
    const existing = editing ?? null;
    const id = existing ? existing.id : slugify(value.name);
    if (!id) return 'Give the plan a name with a letter or a number in it.';
    if (!existing && all.some((plan) => plan.id === id)) return 'A plan with this name already exists.';
    try {
      await savePlanLibraryPlan(id, value, admin.uid, !existing);
      toast(existing ? 'Changes saved' : `${value.name} added`);
      setEditing(undefined);
      reload();
      return undefined;
    } catch (failure) {
      return messageOf(failure);
    }
  }

  return (
    <Page
      title="Plan library"
      description="Ready-made plans, built from the exercise library. Your clients browse them in the app and put one on a day, with or without a trainer. You can also send one to a client."
      actions={(
        <>
          <Button variant="outline" type="button" onClick={() => void importStarter()}><Upload /> Import starter plans</Button>
          <Button type="button" onClick={() => setEditing(null)}><Plus /> New plan</Button>
        </>
      )}
    >
      {error ? <ErrorBox error={error} /> : null}
      {!plans ? (error ? null : <PageSkeleton stats={2} />) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Stat icon={ListChecks} label="Plans" value={all.length} />
            <Stat icon={Eye} label="Shown to users" value={visible} />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <SearchBox className="sm:w-80" value={query} onChange={setQuery} placeholder="Search by name, description or level" label="Search the plan library" />
            <FilterToggle label="Visibility" value={status} options={statusOptions} onChange={setStatus} />
          </div>
          {shown.length ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {shown.map((plan) => {
                const missing = exercises.length ? unknownExercises(plan, exercises) : [];
                return (
                  <Card key={plan.id} className={cn(!plan.active && 'opacity-60')}>
                    <CardHeader>
                      <CardTitle className="flex flex-wrap items-center gap-2 text-base">
                        {plan.name}
                        {plan.level ? <ToneBadge tone="info">{plan.level}</ToneBadge> : null}
                        {!plan.active ? <ToneBadge tone="warn">Hidden</ToneBadge> : null}
                      </CardTitle>
                      <CardDescription>
                        {[exerciseCount(plan), planGroups(plan, exercises).join(', ')].filter(Boolean).join(' · ')}
                      </CardDescription>
                      <CardAction className="flex items-center gap-2">
                        <Switch checked={plan.active} aria-label={`${plan.name} is shown to users`} onCheckedChange={() => void toggle(plan)} />
                        <DropdownMenu>
                          <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" aria-label={`Actions for ${plan.name}`} />}>
                            <MoreHorizontal />
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="min-w-40">
                            <DropdownMenuItem onClick={() => setEditing(plan)}><Pencil /> Edit</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => void toggle(plan)}>{plan.active ? <EyeOff /> : <Eye />}{plan.active ? 'Hide from users' : 'Show to users'}</DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem variant="destructive" onClick={() => void remove(plan)}><Trash2 /> Delete</DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </CardAction>
                    </CardHeader>
                    <CardContent className="space-y-3">
                      {plan.description ? <p className="text-sm text-muted-foreground">{plan.description}</p> : null}
                      <ul className="divide-y text-sm">
                        {plan.exercises.map((exercise, index) => (
                          <li key={`${exercise.name}-${index}`} className="flex flex-wrap justify-between gap-x-3 py-1.5">
                            <strong>{exercise.name}</strong>
                            <span className="text-muted-foreground">{describeAssigned(exercise)}</span>
                          </li>
                        ))}
                      </ul>
                      {missing.length ? (
                        <p className="text-sm text-destructive" role="alert">
                          <Badge variant="destructive" className="mr-2">Check</Badge>
                          {`Not in the exercise library: ${missing.join(', ')}. The app still shows it, but it has no details or muscles.`}
                        </p>
                      ) : null}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          ) : (
            <Empty>{all.length ? 'No plan matches.' : 'The plan library is empty. Import the starter plans to begin, or build one from the exercise library.'}</Empty>
          )}
        </>
      )}

      {editing !== undefined ? (
        <Modal title={editing ? `Edit ${editing.name}` : 'New plan'} onClose={() => setEditing(undefined)}>
          <PlanLibraryForm initial={editing ? draftFromPlan(editing) : null} library={exercises} onSave={save} onCancel={() => setEditing(undefined)} />
        </Modal>
      ) : null}
    </Page>
  );
}
