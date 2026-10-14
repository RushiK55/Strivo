'use client';
// The exercise library: what every user can browse in the app. Admins add, edit, hide and remove exercises here.
import { Eye, EyeOff, Layers, ListChecks, MoreHorizontal, Pencil, PersonStanding, Plus, Trash2, Upload } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { TablePagination } from '@/components/TablePagination';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, Stat, type FilterOption } from '@/components/ui';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Switch } from '@/components/ui/switch';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { deleteLibraryExercise, importLibrary, listLibrary, saveLibraryExercise, setLibraryActive, setLibraryMuscles } from '@/lib/data';
import { usePagination, useLoad } from '@/lib/hooks';
import { filterLibrary, missingFromLibrary, MUSCLE_GROUPS, musclesToFill, slugify, type LibraryFilters } from '@/lib/logic';
import { loadCatalogue, muscleLabel } from '@/lib/muscles';
import type { LibraryExercise, LibraryFields } from '@/lib/types';
import { cn } from '@/lib/utils';
import { ExerciseForm } from './ExerciseForm';

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

/** The starter library that ships with the dashboard (also the app's first library). */
const loadStarter = async () => (await import('../../seed/library.json')).default as unknown as (LibraryFields & { id: string })[];

/** `null` is the dialog for a new exercise, an exercise is the dialog to edit it, `undefined` is no dialog. */
type Editing = LibraryExercise | null | undefined;

export function LibraryView() {
  const admin = useAdmin();
  const { toast, confirm } = useFeedback();
  const { data: exercises, error, reload } = useLoad(listLibrary);
  const [filters, setFilters] = useState<LibraryFilters>({ query: '', group: '', status: 'all' });
  const [editing, setEditing] = useState<Editing>(undefined);
  const groups = useLoad(loadCatalogue).data?.groups;
  const [starter, setStarter] = useState<Awaited<ReturnType<typeof loadStarter>>>([]);
  useEffect(() => { void loadStarter().then(setStarter, () => setStarter([])); }, []);

  const all = exercises ?? [];
  const shown = filterLibrary(all, filters);
  const paging = usePagination(shown.length, 10);
  const visible = all.filter((exercise) => exercise.active).length;
  const filter = (next: Partial<LibraryFilters>) => { setFilters({ ...filters, ...next }); paging.setPage(1); };

  const statusOptions: FilterOption<LibraryFilters['status']>[] = [
    { value: 'all', label: 'All', count: all.length },
    { value: 'active', label: 'Shown', count: visible },
    { value: 'hidden', label: 'Hidden', count: all.length - visible },
  ];

  async function toggle(exercise: LibraryExercise) {
    try {
      await setLibraryActive(exercise.id, !exercise.active, admin.uid);
      toast(exercise.active ? `${exercise.name} is now hidden from users` : `${exercise.name} is now shown to users`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function remove(exercise: LibraryExercise) {
    const ok = await confirm({
      title: 'Delete exercise',
      message: `Delete "${exercise.name}" from the library? Users who already added it to a plan keep their copy.`,
      confirmText: 'Delete',
    });
    if (!ok) return;
    try {
      await deleteLibraryExercise(exercise.id);
      toast(`${exercise.name} deleted`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  const toFill = musclesToFill(starter, all);

  async function fillMuscles() {
    const ok = await confirm({
      title: 'Fill in muscles',
      message: `Set the main and helping muscles of ${toFill.length} exercises from the starter library? Only exercises without muscles are changed.`,
      confirmText: 'Fill in',
    });
    if (!ok) return;
    try {
      const written = await setLibraryMuscles(toFill, admin.uid);
      toast(`Muscles set on ${written} exercises`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function importStarter() {
    try {
      const seed = await loadStarter();
      const fresh = missingFromLibrary(seed, all.map((exercise) => exercise.id));
      if (!fresh.length) { toast('The starter library is already imported.'); return; }
      const ok = await confirm({
        title: 'Import starter library',
        message: `Add ${fresh.length} starter exercises? Exercises already in the library are not changed.`,
        confirmText: 'Import',
      });
      if (!ok) return;
      const written = await importLibrary(fresh, admin.uid);
      toast(`${written} exercises imported`);
      reload();
    } catch (failure) {
      toast(messageOf(failure), 'error');
    }
  }

  async function save(value: LibraryFields): Promise<string | undefined> {
    const existing = editing ?? null;
    const id = existing ? existing.id : slugify(value.name);
    if (!existing && all.some((exercise) => exercise.id === id)) return 'An exercise with this name already exists.';
    try {
      await saveLibraryExercise(id, value, admin.uid, !existing);
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
      title="Exercise library"
      description="The exercises your clients can browse in the app and add to their plans."
      actions={(
        <>
          {toFill.length ? <Button variant="outline" type="button" onClick={() => void fillMuscles()}><PersonStanding /> {`Fill in muscles (${toFill.length})`}</Button> : null}
          <Button variant="outline" type="button" onClick={() => void importStarter()}><Upload /> Import starter library</Button>
          <Button type="button" onClick={() => setEditing(null)}><Plus /> New exercise</Button>
        </>
      )}
    >
      {error ? <ErrorBox error={error} /> : null}
      {!exercises ? (error ? null : <PageSkeleton stats={3} />) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat icon={ListChecks} label="Exercises" value={all.length} />
            <Stat icon={Eye} label="Shown to users" value={visible} />
            <Stat icon={Layers} label="Muscle groups" value={new Set(all.map((exercise) => exercise.muscleGroup)).size} />
          </div>
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">All exercises</CardTitle>
              <CardDescription>{`${shown.length} of ${all.length} exercises`}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                <SearchBox className="sm:w-80" value={filters.query} onChange={(query) => filter({ query })} placeholder="Search by name, muscle or equipment" label="Search the library" />
                <NativeSelect aria-label="Muscle group" value={filters.group} onChange={(event) => filter({ group: event.target.value })}>
                  <NativeSelectOption value="">All muscle groups</NativeSelectOption>
                  {MUSCLE_GROUPS.map((group) => <NativeSelectOption key={group} value={group}>{group}</NativeSelectOption>)}
                </NativeSelect>
                <FilterToggle label="Visibility" value={filters.status} options={statusOptions} onChange={(status) => filter({ status })} />
              </div>
              {shown.length ? (
                <div className="rounded-lg border">
                  <Table>
                    <TableHeader>
                      <TableRow>{['Exercise', 'Muscle group', 'Main muscles', 'Equipment', 'Suggested', 'Shown to users', ''].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
                    </TableHeader>
                    <TableBody>
                      {shown.slice(paging.start, paging.end).map((exercise) => (
                        <TableRow key={exercise.id} className={cn(!exercise.active && 'opacity-60')}>
                          <TableCell>
                            <div className="flex flex-col"><strong>{exercise.name}</strong><span className="text-muted-foreground">{exercise.level || '–'}</span></div>
                          </TableCell>
                          <TableCell><Badge variant="outline">{exercise.muscleGroup}</Badge></TableCell>
                          <TableCell className="max-w-56 whitespace-normal">
                            {exercise.primaryMuscles?.length
                              ? exercise.primaryMuscles.map((id) => muscleLabel(groups, id)).join(', ')
                              : <span className="text-muted-foreground">Not set</span>}
                          </TableCell>
                          <TableCell>{exercise.equipment || '–'}</TableCell>
                          <TableCell>{`${exercise.defaultSets} × ${exercise.defaultReps}${exercise.defaultWeightKg > 0 ? ` @ ${exercise.defaultWeightKg} kg` : ''}`}</TableCell>
                          <TableCell>
                            <Switch checked={exercise.active} aria-label={`${exercise.name} is shown to users`} onCheckedChange={() => void toggle(exercise)} />
                          </TableCell>
                          <TableCell className="text-right">
                            <DropdownMenu>
                              <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" aria-label={`Actions for ${exercise.name}`} />}>
                                <MoreHorizontal />
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end" className="min-w-40">
                                <DropdownMenuItem onClick={() => setEditing(exercise)}><Pencil /> Edit</DropdownMenuItem>
                                <DropdownMenuItem onClick={() => void toggle(exercise)}>{exercise.active ? <EyeOff /> : <Eye />}{exercise.active ? 'Hide from users' : 'Show to users'}</DropdownMenuItem>
                                <DropdownMenuSeparator />
                                <DropdownMenuItem variant="destructive" onClick={() => void remove(exercise)}><Trash2 /> Delete</DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <Empty>{all.length ? 'No exercise matches.' : 'The library is empty. Import the starter library to begin, or add exercises one by one.'}</Empty>
              )}
            </CardContent>
            <CardFooter className="border-t">
              <div className="w-full">
                <TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="exercises" />
              </div>
            </CardFooter>
          </Card>
        </>
      )}

      {editing !== undefined ? (
        <Modal title={editing ? `Edit ${editing.name}` : 'New exercise'} onClose={() => setEditing(undefined)}>
          <ExerciseForm initial={editing} onSave={save} onCancel={() => setEditing(undefined)} />
        </Modal>
      ) : null}
    </Page>
  );
}
