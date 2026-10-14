'use client';
// Picks muscle groups: the chosen ones as removable chips, and a list to add another from. Used on the library form for the
// muscles an exercise trains. Works without the catalogue too (it then shows the ids), so the form never depends on it.
import { X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { NativeSelect, NativeSelectOptGroup, NativeSelectOption } from '@/components/ui/native-select';
import { MUSCLE_GROUPS } from '@/lib/logic';
import { muscleLabel, type MuscleGroup } from '@/lib/muscles';

export interface MusclePickerProps {
  label: string;
  value: string[];
  onChange: (value: string[]) => void;
  /** The catalogue's groups; undefined while they load or when they could not be loaded. */
  groups: MuscleGroup[] | undefined;
  /** Muscles that cannot be chosen here (the ones already chosen in the other list). */
  exclude?: string[];
  max: number;
}

export function MusclePicker({ label, value, onChange, groups, exclude = [], max }: MusclePickerProps) {
  const choices = (groups ?? []).filter((group) => !value.includes(group.id) && !exclude.includes(group.id));
  const byArea = MUSCLE_GROUPS.map((area) => ({ area, items: choices.filter((group) => group.libraryGroup === area).sort((a, b) => a.label.localeCompare(b.label)) })).filter((entry) => entry.items.length);
  return (
    <div className="grid gap-2">
      <div className="flex min-h-7 flex-wrap items-center gap-1.5" role="list" aria-label={`${label}: chosen`}>
        {value.length ? value.map((id) => (
          <Badge key={id} variant="secondary" className="gap-1 pr-1" role="listitem">
            {muscleLabel(groups, id)}
            <button
              type="button"
              className="grid size-4 place-items-center rounded-full hover:bg-foreground/15 focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={`Remove ${muscleLabel(groups, id)}`}
              onClick={() => onChange(value.filter((other) => other !== id))}
            >
              <X className="size-3" aria-hidden="true" />
            </button>
          </Badge>
        )) : <span className="text-sm text-muted-foreground">None chosen</span>}
      </div>
      {groups ? (
        <NativeSelect
          className="w-full"
          aria-label={`Add to ${label}`}
          value=""
          disabled={value.length >= max || !choices.length}
          onChange={(event) => { if (event.target.value) onChange([...value, event.target.value]); }}
        >
          <NativeSelectOption value="">{value.length >= max ? `At most ${max}` : 'Add a muscle…'}</NativeSelectOption>
          {byArea.map(({ area, items }) => (
            <NativeSelectOptGroup key={area} label={area}>
              {items.map((group) => <NativeSelectOption key={group.id} value={group.id}>{group.label}</NativeSelectOption>)}
            </NativeSelectOptGroup>
          ))}
        </NativeSelect>
      ) : <p className="text-xs text-muted-foreground">The muscle list could not be loaded, so muscles cannot be added now.</p>}
    </div>
  );
}
