'use client';
// The form for adding or editing one library exercise. It checks the input with the same rules firestore.rules enforces.
import { useState, type FormEvent } from 'react';
import { Field } from '@/components/ui';
import { LEVELS, MUSCLE_GROUPS, validateLibraryExercise, type LibraryErrors } from '@/lib/logic';
import type { LibraryFields, LibraryInput } from '@/lib/types';

const BLANK: LibraryInput = {
  name: '', muscleGroup: 'Chest', equipment: '', level: 'Beginner', instructions: '', tips: '',
  defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true,
};

export interface ExerciseFormProps {
  /** The exercise being edited; null for a new one. */
  initial: LibraryInput | null;
  /** Called with the checked values. Resolves to an error for the Name field, or nothing when saved. */
  onSave: (value: LibraryFields) => Promise<string | undefined>;
  onCancel: () => void;
}

export function ExerciseForm({ initial, onSave, onCancel }: ExerciseFormProps) {
  const [values, setValues] = useState<LibraryInput>(initial ?? BLANK);
  const [errors, setErrors] = useState<LibraryErrors>({});
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof LibraryInput>(key: K, value: LibraryInput[K]) => setValues((current) => ({ ...current, [key]: value }));
  const text = (key: 'name' | 'equipment' | 'videoUrl') => ({ value: values[key], onChange: (event: { target: { value: string } }) => set(key, event.target.value) });
  const number = (key: 'defaultSets' | 'defaultReps' | 'defaultWeightKg') => ({ value: values[key], onChange: (event: { target: { value: string } }) => set(key, event.target.value) });

  async function submit(event: FormEvent) {
    event.preventDefault();
    const result = validateLibraryExercise(values);
    if (!result.ok) { setErrors(result.errors); return; }
    setBusy(true);
    const problem = await onSave(result.value);
    setBusy(false);
    setErrors(problem ? { name: problem } : {});
  }

  const groups = [...new Set([...MUSCLE_GROUPS, values.muscleGroup])];
  return (
    <form className="form" noValidate onSubmit={(event) => void submit(event)}>
      <Field label="Name" error={errors.name}><input type="text" name="name" maxLength={100} {...text('name')} /></Field>
      <div className="two">
        <Field label="Muscle group" error={errors.muscleGroup}>
          <select name="muscleGroup" value={values.muscleGroup} onChange={(event) => set('muscleGroup', event.target.value)}>
            {groups.map((group) => <option key={group} value={group}>{group}</option>)}
          </select>
        </Field>
        <Field label="Level" error={errors.level}>
          <select name="level" value={values.level} onChange={(event) => set('level', event.target.value)}>
            {LEVELS.map((level) => <option key={level} value={level}>{level}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Equipment" error={errors.equipment} hint="For example Barbell, Dumbbells, Machine, Bodyweight">
        <input type="text" name="equipment" maxLength={40} {...text('equipment')} />
      </Field>
      <Field label="How to do it" error={errors.instructions}>
        <textarea name="instructions" rows={4} maxLength={4000} value={values.instructions} onChange={(event) => set('instructions', event.target.value)} />
      </Field>
      <Field label="Tip" error={errors.tips}>
        <textarea name="tips" rows={2} maxLength={1000} value={values.tips} onChange={(event) => set('tips', event.target.value)} />
      </Field>
      <div className="three">
        <Field label="Sets" error={errors.defaultSets}><input type="number" name="defaultSets" min={1} max={20} step={1} {...number('defaultSets')} /></Field>
        <Field label="Reps" error={errors.defaultReps}><input type="number" name="defaultReps" min={1} max={100} step={1} {...number('defaultReps')} /></Field>
        <Field label="Weight (kg)" error={errors.defaultWeightKg}><input type="number" name="defaultWeightKg" min={0} max={1000} step={0.5} {...number('defaultWeightKg')} /></Field>
      </div>
      <Field label="Video link" error={errors.videoUrl} hint="Optional. Must start with https://">
        <input type="url" name="videoUrl" placeholder="https://…" {...text('videoUrl')} />
      </Field>
      <label className="check">
        <input type="checkbox" name="active" checked={values.active} onChange={(event) => set('active', event.target.checked)} /> Show this exercise to users
      </label>
      <div className="actions">
        <button className="btn" type="button" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" type="submit" disabled={busy}>{initial ? 'Save changes' : 'Add to library'}</button>
      </div>
    </form>
  );
}
