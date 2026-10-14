'use client';
// The 3D body: a skin layer over the muscles. Hover a muscle and it lights up; click it and the skin fades away, the muscle glows
// and the rest step back. Then read what it does and see which library exercises train it.
// Trainers use it to pick exercises for a client; the same catalogue and exercises feed the app. The deep muscles lie under the
// surface ones, so the page can fade the surface layer, and every muscle is also in a list, which is the way to reach one with the
// keyboard.
import { ArrowLeft, ArrowLeftToLine, ArrowRightToLine, Layers, PersonStanding, RotateCcw } from 'lucide-react';
import dynamic from 'next/dynamic';
import { useMemo, useState } from 'react';
import type { ViewerStatus } from '@/components/BodyViewer';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, type FilterOption } from '@/components/ui';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import type { PickedMuscle } from '@/lib/bodyScene';
import { listLibrary } from '@/lib/data';
import { safeHref } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import { MUSCLE_GROUPS } from '@/lib/logic';
import { countExercises, exercisesForMuscle, loadCatalogue, type BodyKey, type MuscleGroup } from '@/lib/muscles';
import type { LibraryExercise } from '@/lib/types';

// three.js is large and only this page needs it: load it when the page opens, and never on the server.
const BodyViewer = dynamic(() => import('@/components/BodyViewer'), { ssr: false });

const BODY_OPTIONS: FilterOption<BodyKey>[] = [{ value: 'male', label: 'Male' }, { value: 'female', label: 'Female' }];

export function BodyView() {
  const catalogue = useLoad(loadCatalogue);
  const library = useLoad(listLibrary);
  const [body, setBody] = useState<BodyKey>('male');
  const [selected, setSelected] = useState<PickedMuscle | null>(null);
  const [hovered, setHovered] = useState<string | null>(null);
  const [skin, setSkin] = useState(true);
  const [fade, setFade] = useState(false);
  /** The fade was switched on by picking a deep muscle (so it is switched off again with it), not by the user. */
  const [autoFade, setAutoFade] = useState(false);
  const [skeleton, setSkeleton] = useState(false);
  const [status, setStatus] = useState<{ state: ViewerStatus; message?: string }>({ state: 'loading' });
  const [view, setView] = useState<{ side: 'front' | 'back'; n: number } | null>(null);
  const [exercisesOpen, setExercisesOpen] = useState(false);

  const groups = catalogue.data?.groups;
  const group = (id: string | null) => groups?.find((candidate) => candidate.id === id) ?? null;
  const current = group(selected?.id ?? null);

  /**
   * Picks a muscle (from the body or the list), or none. A picked muscle takes the skin off so it can be seen, and a deep one
   * also fades the surface muscles over it; picking none puts the skin back.
   */
  function choose(next: PickedMuscle | null) {
    setSelected(next);
    setExercisesOpen(false);
    setSkin(!next);
    const deep = next ? group(next.id)?.layer === 'deep' : false;
    if (deep && !fade) { setFade(true); setAutoFade(true); }
    if (!deep && autoFade) { setFade(false); setAutoFade(false); }
  }

  if (catalogue.error) return <Page title="Body"><ErrorBox error={catalogue.error} /></Page>;
  if (!catalogue.data || !groups) return <Page title="Body"><PageSkeleton /></Page>;

  return (
    <Page title="Body" description="Hover a muscle to light it up and click it: the skin comes off and you see the muscle, what it does and which exercises train it.">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <Card className="gap-0 overflow-hidden py-0">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b p-3">
            <FilterToggle label="Body" value={body} options={BODY_OPTIONS} onChange={setBody} />
            <div className="flex gap-1.5">
              <Button variant="outline" size="sm" onClick={() => setView({ side: 'front', n: (view?.n ?? 0) + 1 })}><ArrowRightToLine /> Front</Button>
              <Button variant="outline" size="sm" onClick={() => setView({ side: 'back', n: (view?.n ?? 0) + 1 })}><ArrowLeftToLine /> Back</Button>
              <Button variant="outline" size="sm" disabled={!selected && skin} onClick={() => choose(null)}><RotateCcw /> Reset</Button>
            </div>
            <Label className="flex items-center gap-2 text-sm">
              <Switch checked={skin} onCheckedChange={setSkin} aria-label="Show the skin" /> <PersonStanding className="size-4 text-muted-foreground" aria-hidden="true" /> Skin
            </Label>
            <Label className="flex items-center gap-2 text-sm">
              <Switch checked={fade} onCheckedChange={(on) => { setFade(on); setAutoFade(false); }} aria-label="Fade the surface muscles to see the deep ones" /> <Layers className="size-4 text-muted-foreground" aria-hidden="true" /> Fade surface
            </Label>
            <Label className="flex items-center gap-2 text-sm">
              <Switch checked={skeleton} onCheckedChange={setSkeleton} aria-label="Show the skeleton" /> Skeleton
            </Label>
          </div>
          <div className="relative h-[min(78vh,56rem)] min-h-[28rem] bg-gradient-to-b from-card to-black/40">
            <BodyViewer
              catalogue={catalogue.data}
              body={body}
              selected={selected}
              fade={fade}
              skeleton={skeleton}
              skin={skin}
              view={view}
              onSelect={choose}
              onHover={setHovered}
              onStatus={(state, message) => setStatus({ state, message })}
            />
            {status.state === 'loading' ? <div className="absolute inset-0 grid place-items-center text-sm text-muted-foreground" role="status">Loading the 3D body…</div> : null}
            {status.state === 'error' ? <div className="absolute inset-x-4 top-4"><ErrorBox error={status.message ?? 'The 3D body could not be shown.'} /></div> : null}
            {hovered ? (
              <div className="pointer-events-none absolute bottom-3 left-3 rounded-md bg-background/85 px-2.5 py-1 text-sm font-medium shadow" data-testid="hover-label">{group(hovered)?.label ?? hovered}</div>
            ) : null}
            {status.state === 'ready' && !selected ? (
              <div className="pointer-events-none absolute bottom-3 right-3 max-w-60 rounded-md bg-background/70 px-2.5 py-1 text-right text-xs text-muted-foreground">Drag to turn · scroll to zoom · click a muscle</div>
            ) : null}
          </div>
        </Card>

        <Card className="max-h-[min(78vh,56rem)] min-h-96 overflow-y-auto" data-testid="muscle-panel">
          {current ? (
            <MuscleDetails
              group={current}
              body={body}
              fade={fade}
              exercises={library.data}
              libraryError={library.error}
              open={exercisesOpen}
              onToggle={() => setExercisesOpen((value) => !value)}
              onBack={() => { choose(null); }}
            />
          ) : (
            <MuscleList groups={groups} hovered={hovered} onPick={(id) => choose({ id, side: 'C' })} />
          )}
        </Card>
      </div>
      <Credits />
    </Page>
  );
}

// ---- The list of every muscle ----

function MuscleList({ groups, hovered, onPick }: { groups: MuscleGroup[]; hovered: string | null; onPick: (id: string) => void }) {
  const [query, setQuery] = useState('');
  const text = query.trim().toLowerCase();
  const sections = useMemo(() => MUSCLE_GROUPS
    .map((area) => ({ area, items: groups.filter((g) => g.libraryGroup === area && (!text || g.label.toLowerCase().includes(text) || g.about.toLowerCase().includes(text))).sort((a, b) => a.label.localeCompare(b.label)) }))
    .filter((section) => section.items.length), [groups, text]);
  return (
    <>
      <CardHeader>
        <CardTitle className="text-lg">Muscles</CardTitle>
        <CardDescription>Click one on the body, or choose it here.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <SearchBox className="sm:w-full" value={query} onChange={setQuery} placeholder="Search muscles" label="Search muscles" />
        {sections.length ? sections.map(({ area, items }) => (
          <section key={area} className="space-y-1.5">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{area}</h3>
            <ul className="flex flex-wrap gap-1.5">
              {items.map((item) => (
                <li key={item.id}>
                  <Button variant={hovered === item.id ? 'secondary' : 'outline'} size="sm" onClick={() => onPick(item.id)} data-muscle={item.id}>
                    {item.label}{item.layer === 'deep' ? <span className="text-xs text-muted-foreground">deep</span> : null}
                  </Button>
                </li>
              ))}
            </ul>
          </section>
        )) : <Empty>No muscle matches.</Empty>}
      </CardContent>
    </>
  );
}

// ---- One muscle ----

interface DetailsProps {
  group: MuscleGroup;
  body: BodyKey;
  fade: boolean;
  exercises: LibraryExercise[] | undefined;
  libraryError: unknown;
  open: boolean;
  onToggle: () => void;
  onBack: () => void;
}

function MuscleDetails({ group, body, fade, exercises, libraryError, open, onToggle, onBack }: DetailsProps) {
  const found = useMemo(() => (exercises ? exercisesForMuscle(exercises, group) : null), [exercises, group]);
  return (
    <>
      <CardHeader>
        <Button variant="ghost" size="sm" className="-ml-2 w-fit text-muted-foreground" onClick={onBack}><ArrowLeft /> All muscles</Button>
        <CardTitle className="text-xl" data-testid="muscle-title">{group.label}</CardTitle>
        <div className="flex flex-wrap gap-1.5">
          <Badge variant="outline">{group.libraryGroup}</Badge>
          <Badge variant={group.layer === 'deep' ? 'secondary' : 'outline'}>{group.layer === 'deep' ? 'Deep muscle' : 'Surface muscle'}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="leading-relaxed">{group.about}</p>
        {group.layer === 'deep' && !fade ? <p className="text-sm text-muted-foreground">This muscle lies under the surface muscles. Switch on “Fade surface” to see it.</p> : null}
        {body === 'female' && !group.female ? <p className="text-sm text-muted-foreground">Not in the female model.</p> : null}
        <Button variant={open ? 'outline' : 'default'} onClick={onToggle} aria-expanded={open} className="w-full" disabled={!found && !libraryError}>
          {open ? 'Hide exercises' : found ? `Show exercises (${countExercises(found)})` : 'Show exercises'}
        </Button>
        {open ? (
          libraryError ? <ErrorBox error={libraryError} />
            : !found ? <div className="space-y-2"><Skeleton className="h-10" /><Skeleton className="h-10" /></div>
              : <ExerciseSections group={group} found={found} />
        ) : null}
      </CardContent>
    </>
  );
}

function ExerciseSections({ group, found }: { group: MuscleGroup; found: ReturnType<typeof exercisesForMuscle> }) {
  if (!countExercises(found)) return <Empty>No exercise in the library trains this muscle yet. Add one, or set its muscles, in the Library.</Empty>;
  return (
    <div className="space-y-4" data-testid="muscle-exercises">
      <ExerciseGroup title="Main exercises" items={found.primary} />
      <ExerciseGroup title="Also works this muscle" items={found.secondary} />
      <ExerciseGroup title={`More ${group.libraryGroup} exercises`} note="Their muscles are not set yet." items={found.general} />
    </div>
  );
}

function ExerciseGroup({ title, note, items }: { title: string; note?: string; items: LibraryExercise[] }) {
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
            </AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    </section>
  );
}

// ---- Credits ----

function Credits() {
  return (
    <Card size="sm">
      <CardContent className="space-y-1 text-sm text-muted-foreground">
        <p>
          <strong className="text-foreground">3D model credits.</strong> Muscles and bones: BodyParts3D (© The Database Center for Life Science). Lats, abs, internal
          oblique, transverse abdominis, quadratus lumborum and multifidus: Z-Anatomy (Gauthier Kervyn, Marcin Zielinski), CC BY-SA 4.0. The female spine and the place of the bust contour in her skin:
          Human Reference Atlas / HuBMAP, CC BY 4.0.
        </p>
        <p>
          The models are shared under CC BY-SA 4.0: use and change them with this credit, and share changes under the same licence.{' '}
          <a className="text-primary underline underline-offset-2" href="/models/anatomy/ATTRIBUTION.md" target="_blank" rel="noreferrer noopener">Full credits and terms</a>. For
          education, not a clinical tool.
        </p>
      </CardContent>
    </Card>
  );
}
