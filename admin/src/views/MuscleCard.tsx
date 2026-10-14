'use client';
// What the floating box on the Body page holds for the muscle that was picked: its name, a subtitle (a friendlier name an admin gave
// it) and what it does, which an admin can edit; and the library exercises that train it, which an admin can change (add an
// exercise, make one a main exercise or a helper, take one off, or write a new one). The box itself (where it floats, the line to
// the muscle) is in BodyView.tsx.
import { Pencil, Plus, X } from 'lucide-react';
import { useMemo, useState, type FormEvent } from 'react';
import { Empty, ErrorBox, FormField, SearchBox } from '@/components/ui';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { safeHref } from '@/lib/format';
import { MAX_ABOUT, MAX_SUBTITLE, validateMuscleInfo, type MuscleRole } from '@/lib/logic';
import { countExercises, exercisesForMuscle, type BodyKey, type MuscleGroup } from '@/lib/muscles';
import type { LibraryExercise } from '@/lib/types';

export interface MuscleCardProps {
  /** The muscle with what admins wrote about it already put in (its subtitle, its description). */
  group: MuscleGroup;
  /** The description the catalogue has for it, which what an admin writes can replace. */
  builtInAbout: string;
  body: BodyKey;
  fade: boolean;
  library: LibraryExercise[] | undefined;
  libraryError: unknown;
  onClose: () => void;
  /** Saves what was written; resolves to a message when it could not be saved. [empty] says nothing of it is left to keep. */
  onSaveInfo: (value: { subtitle: string; about: string }, empty: boolean) => Promise<string | undefined>;
  /** Takes away everything written about the muscle. */
  onResetInfo: () => Promise<void>;
  /** Makes the muscle a main one, a helper or neither for a library exercise. */
  onChangeRole: (exercise: LibraryExercise, role: MuscleRole) => Promise<void>;
  /** Opens the form for a new exercise that trains this muscle. */
  onNewExercise: () => void;
}

type Where = 'primary' | 'secondary' | 'general';

export function MuscleCard(props: MuscleCardProps) {
  const { group, builtInAbout, body, fade, library, libraryError, onClose, onSaveInfo, onResetInfo, onChangeRole, onNewExercise } = props;
  const [exercisesOpen, setExercisesOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const found = useMemo(() => (library ? exercisesForMuscle(library, group) : null), [library, group]);

  return (
    <>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 space-y-1.5">
          <div>
            <h2 className="text-xl font-semibold leading-tight" data-testid="muscle-title">{group.label}</h2>
            {group.subtitle ? <p className="text-sm text-muted-foreground" data-testid="muscle-subtitle">{group.subtitle}</p> : null}
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge variant="outline">{group.libraryGroup}</Badge>
            <Badge variant={group.layer === 'deep' ? 'secondary' : 'outline'}>{group.layer === 'deep' ? 'Deep muscle' : 'Surface muscle'}</Badge>
          </div>
        </div>
        <div className="-mr-2 -mt-2 flex shrink-0">
          <Button variant={editing ? 'secondary' : 'ghost'} size="icon" className="size-8" aria-label="Edit this muscle's info" aria-pressed={editing} onClick={() => setEditing((value) => !value)}><Pencil /></Button>
          <Button variant="ghost" size="icon" className="size-8" aria-label="Close the muscle card" onClick={onClose}><X /></Button>
        </div>
      </div>

      <div className="mt-3 space-y-3">
        {editing ? (
          <InfoForm group={group} builtInAbout={builtInAbout} onSave={onSaveInfo} onReset={onResetInfo} onDone={() => setEditing(false)} />
        ) : (
          <>
            <p className="text-sm leading-relaxed">{group.about}</p>
            {group.layer === 'deep' && !fade ? <p className="text-sm text-muted-foreground">This muscle lies under the surface muscles. Switch on “Fade surface” to see it.</p> : null}
            {body === 'female' && !group.female ? <p className="text-sm text-muted-foreground">Not in the female model.</p> : null}
          </>
        )}

        <Button variant={exercisesOpen ? 'outline' : 'default'} onClick={() => setExercisesOpen((value) => !value)} aria-expanded={exercisesOpen} className="w-full" disabled={!found && !libraryError}>
          {exercisesOpen ? 'Hide exercises' : found ? `Show exercises (${countExercises(found)})` : 'Show exercises'}
        </Button>

        {exercisesOpen ? (
          libraryError ? <ErrorBox error={libraryError} />
            : !found || !library ? <div className="space-y-2"><Skeleton className="h-10" /><Skeleton className="h-10" /></div>
              : (
                <div className="space-y-4" data-testid="muscle-exercises">
                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" aria-expanded={adding} aria-label="Add an exercise to this muscle" onClick={() => setAdding((value) => !value)}><Plus /> Add exercise</Button>
                    <Button variant="outline" size="sm" onClick={onNewExercise}><Plus /> New exercise</Button>
                  </div>
                  {adding ? <ExercisePicker group={group} library={library} onAdd={onChangeRole} /> : null}
                  {countExercises(found) ? null : <Empty>No exercise in the library trains this muscle yet. Add one above.</Empty>}
                  <ExerciseGroup title="Main exercises" where="primary" items={found.primary} group={group} onChangeRole={onChangeRole} />
                  <ExerciseGroup title="Also works this muscle" where="secondary" items={found.secondary} group={group} onChangeRole={onChangeRole} />
                  <ExerciseGroup title={`More ${group.libraryGroup} exercises`} note="Their muscles are not set yet." where="general" items={found.general} group={group} onChangeRole={onChangeRole} />
                </div>
              )
        ) : null}
      </div>
    </>
  );
}

// ---- Editing what is written about the muscle ----

function InfoForm({ group, builtInAbout, onSave, onReset, onDone }: {
  group: MuscleGroup;
  builtInAbout: string;
  onSave: MuscleCardProps['onSaveInfo'];
  onReset: () => Promise<void>;
  onDone: () => void;
}) {
  const [subtitle, setSubtitle] = useState(group.subtitle ?? '');
  const [about, setAbout] = useState(group.about);
  const [errors, setErrors] = useState<{ subtitle?: string; about?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const check = validateMuscleInfo({ subtitle, about }, builtInAbout);
    if (!check.ok) { setErrors(check.errors); return; }
    setBusy(true);
    const problem = await onSave(check.value, check.empty);
    setBusy(false);
    if (problem) setErrors({ form: problem }); else onDone();
  }

  async function reset() {
    setBusy(true);
    await onReset();
    setBusy(false);
    onDone();
  }

  return (
    <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate aria-label={`Edit ${group.label}`}>
      <FormField label="Subtitle" error={errors.subtitle} hint="A friendlier name, shown under the muscle's name. Leave it empty for none.">
        {(id) => <Input id={id} name="subtitle" value={subtitle} maxLength={MAX_SUBTITLE + 20} placeholder="For example: Back wings" onChange={(event) => setSubtitle(event.target.value)} />}
      </FormField>
      <FormField label="What it is and does" error={errors.about} hint={`Up to ${MAX_ABOUT} characters. Clear it, or use the built-in text, to go back to the original.`}>
        {(id) => <Textarea id={id} name="about" rows={6} value={about} onChange={(event) => setAbout(event.target.value)} />}
      </FormField>
      {errors.form ? <p className="text-sm text-destructive" role="alert">{errors.form}</p> : null}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={busy}>Save</Button>
        <Button type="button" size="sm" variant="outline" disabled={busy} onClick={onDone}>Cancel</Button>
        {group.edited ? <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={() => void reset()}>Reset to built-in</Button> : null}
      </div>
    </form>
  );
}

// ---- The exercises ----

/** Library exercises that do not train the muscle yet, to add one as a main exercise or a helper. */
function ExercisePicker({ group, library, onAdd }: { group: MuscleGroup; library: LibraryExercise[]; onAdd: MuscleCardProps['onChangeRole'] }) {
  const [query, setQuery] = useState('');
  const text = query.trim().toLowerCase();
  const matches = useMemo(() => library
    .filter((exercise) => exercise.active && !exercise.primaryMuscles?.includes(group.id) && !exercise.secondaryMuscles?.includes(group.id))
    .filter((exercise) => !text || `${exercise.name} ${exercise.equipment} ${exercise.muscleGroup}`.toLowerCase().includes(text))
    .sort((a, b) => Number(b.muscleGroup === group.libraryGroup) - Number(a.muscleGroup === group.libraryGroup) || a.name.localeCompare(b.name)), [library, group, text]);
  return (
    <div className="space-y-2 rounded-lg border p-3" data-testid="exercise-picker">
      <SearchBox className="sm:w-full" value={query} onChange={setQuery} placeholder="Search the library" label="Search the library" />
      {matches.length ? (
        <ul className="max-h-64 divide-y overflow-y-auto">
          {matches.slice(0, 30).map((exercise) => (
            <li key={exercise.id} className="flex items-center justify-between gap-2 py-2">
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{exercise.name}</span>
                <span className="block truncate text-xs text-muted-foreground">{[exercise.muscleGroup, exercise.equipment].filter(Boolean).join(' · ')}</span>
              </span>
              <span className="flex shrink-0 gap-1.5">
                <Button size="sm" variant="outline" aria-label={`Add ${exercise.name} as a main exercise`} onClick={() => void onAdd(exercise, 'primary')}>Main</Button>
                <Button size="sm" variant="outline" aria-label={`Add ${exercise.name} as a helper`} onClick={() => void onAdd(exercise, 'secondary')}>Helper</Button>
              </span>
            </li>
          ))}
        </ul>
      ) : <p className="text-sm text-muted-foreground">{text ? 'No exercise matches.' : 'Every exercise in the library is already here.'}</p>}
    </div>
  );
}

function ExerciseGroup({ title, note, where, items, group, onChangeRole }: {
  title: string;
  note?: string;
  where: Where;
  items: LibraryExercise[];
  group: MuscleGroup;
  onChangeRole: MuscleCardProps['onChangeRole'];
}) {
  if (!items.length) return null;
  return (
    <section className="space-y-1.5">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title} · {items.length}</h3>
      {note ? <p className="text-xs text-muted-foreground">{note}</p> : null}
      <Accordion className="rounded-lg border px-3">
        {items.map((exercise) => (
          <AccordionItem key={exercise.id} value={exercise.id}>
            <AccordionTrigger>
              <span className="flex min-w-0 flex-col items-start text-left">
                <strong className="font-medium">{exercise.name}</strong>
                <span className="text-xs font-normal text-muted-foreground">
                  {[exercise.equipment, exercise.level, `${exercise.defaultSets} × ${exercise.defaultReps}`].filter(Boolean).join(' · ')}
                </span>
              </span>
            </AccordionTrigger>
            <AccordionContent className="space-y-2">
              {exercise.instructions ? <p>{exercise.instructions}</p> : null}
              {exercise.tips ? <p className="text-muted-foreground">{`Tip: ${exercise.tips}`}</p> : null}
              {safeHref(exercise.videoUrl) ? <a className="text-primary underline underline-offset-2" href={safeHref(exercise.videoUrl) ?? undefined} target="_blank" rel="noreferrer noopener">Watch a video</a> : null}
              <div className="flex flex-wrap gap-2 pt-1">
                {where !== 'primary' ? <Button size="sm" variant="outline" onClick={() => void onChangeRole(exercise, 'primary')}>{where === 'general' ? 'Main exercise' : 'Make it a main exercise'}</Button> : null}
                {where !== 'secondary' ? <Button size="sm" variant="outline" onClick={() => void onChangeRole(exercise, 'secondary')}>{where === 'general' ? 'Helper' : 'Make it a helper'}</Button> : null}
                {where !== 'general' ? <Button size="sm" variant="ghost" aria-label={`Remove ${exercise.name} from ${group.label}`} onClick={() => void onChangeRole(exercise, null)}>{`Remove from ${group.label}`}</Button> : null}
              </div>
            </AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    </section>
  );
}
