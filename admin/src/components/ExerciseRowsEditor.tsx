'use client';
// The list of exercises in a plan form (the plan library's, or the one a trainer sends): a row for each exercise with its sets, reps,
// weight, drop sets and notes, a button to add a blank row, and a picker that adds an exercise from the exercise library with its
// suggested sets, reps and weight. The rows' numbers are text until the form is checked (lib/assign.ts, checkExerciseRows).
// The order is the order of the plan: grab a row by its handle and drag it into place (mouse or touch, like in the app), or focus the
// handle and use the up and down arrow keys.
import { GripVertical, Plus, Trash2 } from 'lucide-react';
import { useState, type KeyboardEvent, type PointerEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect, NativeSelectOptGroup, NativeSelectOption } from '@/components/ui/native-select';
import { MAX_ASSIGNED_EXERCISES, blankExercise, exerciseFromLibrary, moveRow, notInRows, type DraftExercise } from '@/lib/assign';
import { MUSCLE_GROUPS } from '@/lib/logic';
import type { LibraryExercise } from '@/lib/types';

export interface ExerciseRowsEditorProps {
  rows: DraftExercise[];
  onChange: (rows: DraftExercise[]) => void;
  /** The exercise library, to add from; without it only blank rows can be added. */
  library: LibraryExercise[];
  /** About the list as a whole (none, too many). */
  listError?: string;
  /** One message per row that has a problem, by its key. */
  rowErrors?: Record<number, string>;
}

/** The nearest ancestor that scrolls, so a row dragged to its edge can scroll it. */
function scrollParent(element: HTMLElement | null): HTMLElement | null {
  for (let node = element?.parentElement ?? null; node; node = node.parentElement) {
    const overflow = getComputedStyle(node).overflowY;
    if ((overflow === 'auto' || overflow === 'scroll') && node.scrollHeight > node.clientHeight) return node;
  }
  return null;
}

export function ExerciseRowsEditor({ rows, onChange, library, listError, rowErrors }: ExerciseRowsEditorProps) {
  // The row being dragged. The list is reordered as it passes over the others, so what is shown is always the order it will be saved in.
  const [dragging, setDragging] = useState<number | null>(null);

  function drag(event: PointerEvent<HTMLButtonElement>, key: number) {
    if (dragging !== key) return;
    // Over which row is the pointer? Move the dragged row there.
    const over = document.elementsFromPoint(event.clientX, event.clientY)
      .map((element) => (element as HTMLElement).closest<HTMLElement>('[data-row-key]'))
      .find((row) => row !== null);
    const overKey = over ? Number(over.dataset.rowKey) : null;
    if (overKey !== null && overKey !== key) {
      const from = rows.findIndex((row) => row.key === key);
      const to = rows.findIndex((row) => row.key === overKey);
      if (from >= 0 && to >= 0) onChange(moveRow(rows, key, to - from));
    }
    // Near the top or bottom edge of what scrolls, scroll it.
    const scroller = scrollParent(event.currentTarget);
    if (scroller) {
      const box = scroller.getBoundingClientRect();
      if (event.clientY < box.top + 48) scroller.scrollBy(0, -16);
      else if (event.clientY > box.bottom - 48) scroller.scrollBy(0, 16);
    }
  }

  function arrows(event: KeyboardEvent<HTMLButtonElement>, key: number) {
    const by = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
    if (!by) return;
    event.preventDefault();
    onChange(moveRow(rows, key, by));
    // The handle moves with its row; keep the focus on it.
    requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-row-key="${key}"] [data-handle]`)?.focus());
  }

  const setRow = (key: number, field: keyof Omit<DraftExercise, 'key'>, value: string) =>
    onChange(rows.map((row) => (row.key === key ? { ...row, [field]: value } : row)));
  // A new row replaces the blank one the form starts with, rather than sitting under it.
  const append = (row: DraftExercise) => onChange([...rows.filter((r) => r.name.trim() || r.notes.trim()), row].slice(0, MAX_ASSIGNED_EXERCISES));
  const remove = (key: number) => onChange(rows.filter((row) => row.key !== key));

  // What is already in the plan is not offered again.
  const byGroup = new Map<string, LibraryExercise[]>();
  for (const exercise of notInRows(library.filter((e) => e.active), rows).sort((a, b) => a.name.localeCompare(b.name))) {
    byGroup.set(exercise.muscleGroup, [...(byGroup.get(exercise.muscleGroup) ?? []), exercise]);
  }
  const groups = [...byGroup.keys()].sort((a, b) => (MUSCLE_GROUPS.indexOf(a) + 1 || 99) - (MUSCLE_GROUPS.indexOf(b) + 1 || 99));
  const full = rows.length >= MAX_ASSIGNED_EXERCISES;

  return (
    <fieldset className="grid gap-3">
      <legend className="mb-2 text-sm font-medium">Exercises</legend>
      {rows.map((row, index) => {
        const rowError = rowErrors?.[row.key];
        return (
          <div
            key={row.key}
            data-row-key={row.key}
            className={`grid gap-2 rounded-lg border p-3 transition-shadow data-[invalid=true]:border-destructive ${dragging === row.key ? 'border-primary bg-muted/40 shadow-lg ring-2 ring-primary/40' : ''}`}
            data-invalid={rowError ? true : undefined}
          >
            <div className="flex items-center gap-2">
              <button
                type="button"
                data-handle
                aria-label={`Drag exercise ${index + 1} to move it`}
                aria-keyshortcuts="ArrowUp ArrowDown"
                title="Drag to reorder"
                className="-ml-1 grid h-9 w-7 shrink-0 cursor-grab touch-none place-items-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring active:cursor-grabbing"
                onPointerDown={(event) => { event.currentTarget.setPointerCapture(event.pointerId); setDragging(row.key); }}
                onPointerMove={(event) => drag(event, row.key)}
                onPointerUp={() => setDragging(null)}
                onPointerCancel={() => setDragging(null)}
                onKeyDown={(event) => arrows(event, row.key)}
              >
                <GripVertical className="size-4" aria-hidden="true" />
              </button>
              <span className="w-5 shrink-0 text-center text-sm text-muted-foreground" aria-hidden="true">{index + 1}</span>
              <Input aria-label={`Exercise ${index + 1} name`} name="exerciseName" maxLength={80} placeholder="Exercise name" value={row.name} onChange={(event) => setRow(row.key, 'name', event.target.value)} />
              <Button type="button" variant="ghost" size="icon" aria-label={`Remove exercise ${index + 1}`} onClick={() => remove(row.key)}><Trash2 /></Button>
            </div>
            <div className="grid grid-cols-4 gap-2">
              {([['sets', 'Sets', 1], ['reps', 'Reps', 1], ['weightKg', 'Kg', 0.5], ['dropSets', 'Drops', 1]] as const).map(([field, label, step]) => (
                <label key={field} className="grid gap-1 text-xs text-muted-foreground">
                  {label}
                  <Input type="number" inputMode="decimal" min={0} step={step} name={field} aria-label={`Exercise ${index + 1} ${label.toLowerCase()}`} value={row[field]} onChange={(event) => setRow(row.key, field, event.target.value)} />
                </label>
              ))}
            </div>
            <Input aria-label={`Exercise ${index + 1} notes`} maxLength={500} placeholder="Notes (optional)" value={row.notes} onChange={(event) => setRow(row.key, 'notes', event.target.value)} />
            {rowError ? <p className="text-sm text-destructive" role="alert">{rowError}</p> : null}
          </div>
        );
      })}
      {listError ? <p className="text-sm text-destructive" role="alert">{listError}</p> : null}
      <div className="grid gap-2 sm:grid-cols-[auto_1fr]">
        <Button type="button" variant="outline" disabled={full} onClick={() => onChange([...rows, blankExercise()])}>
          <Plus /> Add exercise
        </Button>
        {byGroup.size ? (
          <NativeSelect className="w-full" aria-label="Add from the exercise library" value="" disabled={full}
            onChange={(event) => { const pick = library.find((e) => e.id === event.target.value); if (pick) append(exerciseFromLibrary(pick)); }}>
            <NativeSelectOption value="">Add from the exercise library…</NativeSelectOption>
            {groups.map((group) => (
              <NativeSelectOptGroup key={group} label={group}>
                {byGroup.get(group)!.map((exercise) => <NativeSelectOption key={exercise.id} value={exercise.id}>{exercise.name}</NativeSelectOption>)}
              </NativeSelectOptGroup>
            ))}
          </NativeSelect>
        ) : null}
      </div>
    </fieldset>
  );
}
