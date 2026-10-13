'use client';
// The exercise library: what every user can browse in the app. Admins add, edit, hide and remove exercises here.
import { useState } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { Badge, Empty, ErrorBox, Loading } from '@/components/ui';
import { deleteLibraryExercise, importLibrary, listLibrary, saveLibraryExercise, setLibraryActive } from '@/lib/data';
import { useLoad } from '@/lib/hooks';
import { filterLibrary, missingFromLibrary, MUSCLE_GROUPS, slugify, type LibraryFilters } from '@/lib/logic';
import type { LibraryExercise, LibraryFields } from '@/lib/types';
import { ExerciseForm } from './ExerciseForm';

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

/** `null` is the dialog for a new exercise, an exercise is the dialog to edit it, `undefined` is no dialog. */
type Editing = LibraryExercise | null | undefined;

export function LibraryView() {
  const admin = useAdmin();
  const { toast, confirm } = useFeedback();
  const { data: exercises, error, reload } = useLoad(listLibrary);
  const [filters, setFilters] = useState<LibraryFilters>({ query: '', group: '', status: 'all' });
  const [editing, setEditing] = useState<Editing>(undefined);

  const all = exercises ?? [];
  const shown = filterLibrary(all, filters);

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

  async function importStarter() {
    try {
      const seed = (await import('../../seed/library.json')).default as unknown as (LibraryFields & { id: string })[];
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
    <div className="page">
      <h1>Exercise library</h1>
      <div className="toolbar">
        <input
          type="search" className="search" placeholder="Search by name, muscle or equipment" aria-label="Search the library"
          value={filters.query} onChange={(event) => setFilters({ ...filters, query: event.target.value })}
        />
        <select aria-label="Muscle group" value={filters.group} onChange={(event) => setFilters({ ...filters, group: event.target.value })}>
          <option value="">All muscle groups</option>
          {MUSCLE_GROUPS.map((group) => <option key={group} value={group}>{group}</option>)}
        </select>
        <select aria-label="Visibility" value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value as LibraryFilters['status'] })}>
          <option value="all">Shown and hidden</option>
          <option value="active">Shown to users</option>
          <option value="hidden">Hidden</option>
        </select>
        <span className="spacer" />
        <button className="btn" type="button" onClick={() => void importStarter()}>Import starter library</button>
        <button className="btn btn-primary" type="button" onClick={() => setEditing(null)}>New exercise</button>
      </div>

      {error ? <ErrorBox error={error} /> : null}
      {!exercises ? (error ? null : <Loading />) : (
        <>
          <p className="muted">{`${shown.length} of ${all.length} exercises · ${all.filter((exercise) => exercise.active).length} shown to users`}</p>
          {shown.length ? (
            <table className="table">
              <thead>
                <tr>{['Exercise', 'Muscle group', 'Equipment', 'Level', 'Suggested', 'Visible', ''].map((title) => <th key={title} scope="col">{title}</th>)}</tr>
              </thead>
              <tbody>
                {shown.map((exercise) => (
                  <tr key={exercise.id} className={exercise.active ? '' : 'dim'}>
                    <td><strong>{exercise.name}</strong></td>
                    <td>{exercise.muscleGroup}</td>
                    <td>{exercise.equipment || '–'}</td>
                    <td>{exercise.level || '–'}</td>
                    <td>{`${exercise.defaultSets} × ${exercise.defaultReps}${exercise.defaultWeightKg > 0 ? ` @ ${exercise.defaultWeightKg} kg` : ''}`}</td>
                    <td><Badge tone={exercise.active ? 'good' : 'warn'}>{exercise.active ? 'Shown' : 'Hidden'}</Badge></td>
                    <td className="row-actions">
                      <button className="btn btn-small" type="button" onClick={() => setEditing(exercise)}>Edit</button>
                      <button className="btn btn-small" type="button" onClick={() => void toggle(exercise)}>{exercise.active ? 'Hide' : 'Show'}</button>
                      <button className="btn btn-small btn-danger" type="button" onClick={() => void remove(exercise)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <Empty>{all.length ? 'No exercise matches.' : 'The library is empty. Import the starter library to begin, or add exercises one by one.'}</Empty>
          )}
        </>
      )}

      {editing !== undefined ? (
        <Modal title={editing ? `Edit ${editing.name}` : 'New exercise'} onClose={() => setEditing(undefined)}>
          <ExerciseForm initial={editing} onSave={save} onCancel={() => setEditing(undefined)} />
        </Modal>
      ) : null}
    </div>
  );
}
