'use client';
// The 3D body, filling the whole page with the body in the middle and full control (turn, zoom), a skin layer over the muscles. Hover a muscle and it lights up; click it and the skin fades away, the muscle glows, the rest step back, and a text box floats in the air beside the body,
// joined to the muscle by a line, saying what it does with a button for the library exercises that train it. The controls are in
// the top right corner, behind a three-line button that opens the control panel (with a "Muscles" button that opens the list of
// every muscle, the way to reach one with the keyboard), and the credits for the models are a small button in the bottom right corner. Trainers use it to pick exercises for a client; the same catalogue and exercises feed the app.
import { ArrowLeftToLine, ArrowRightToLine, Info, Layers, List, Menu, PersonStanding, RotateCcw, X } from 'lucide-react';
import dynamic from 'next/dynamic';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import type { ViewerStatus } from '@/components/BodyViewer';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, type FilterOption } from '@/components/ui';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import type { PickedMuscle } from '@/lib/bodyScene';
import { createAnchorStore, maxCardHeight, placeCallout } from '@/lib/callout';
import { listLibrary } from '@/lib/data';
import { safeHref } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import { MUSCLE_GROUPS } from '@/lib/logic';
import { countExercises, exercisesForMuscle, loadCatalogue, type BodyKey, type MuscleGroup } from '@/lib/muscles';
import type { LibraryExercise } from '@/lib/types';

// three.js is large and only this page needs it: load it when the page opens, and never on the server.
const BodyViewer = dynamic(() => import('@/components/BodyViewer'), { ssr: false });

const BODY_OPTIONS: FilterOption<BodyKey>[] = [{ value: 'male', label: 'Male' }, { value: 'female', label: 'Female' }];
/** What the panels on the right take of the stage (their 16 rem and the gaps around it), which the floating box keeps out of; and what the three-line button alone takes. */
const RIGHT_INSET = 284;
const TOGGLE_INSET = 64;

export function BodyView() {
  const catalogue = useLoad(loadCatalogue);
  const library = useLoad(listLibrary);
  const stage = useRef<HTMLDivElement>(null);
  const anchors = useMemo(() => createAnchorStore(), []);
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
  const [listOpen, setListOpen] = useState(false);
  const [creditsOpen, setCreditsOpen] = useState(false);
  /** The control panel behind the three-line button: closed to begin with, so the body has the whole view. */
  const [controlsOpen, setControlsOpen] = useState(false);

  const groups = catalogue.data?.groups;
  const group = (id: string | null) => groups?.find((candidate) => candidate.id === id) ?? null;
  const current = group(selected?.id ?? null);

  /**
   * Picks a muscle (from the body or the list), or none. A picked muscle takes the skin off so it can be seen, and a deep one
   * also fades the surface muscles over it; picking none puts the skin back. The list closes when a muscle is picked from it.
   */
  function choose(next: PickedMuscle | null) {
    setSelected(next);
    setExercisesOpen(false);
    setSkin(!next);
    if (next) setListOpen(false);
    const deep = next ? group(next.id)?.layer === 'deep' : false;
    if (deep && !fade) { setFade(true); setAutoFade(true); }
    if (!deep && autoFade) { setFade(false); setAutoFade(false); }
  }

  // Escape closes the credits first, then the muscle list, then the muscle's box, then the control panel.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (creditsOpen) setCreditsOpen(false);
      else if (listOpen) setListOpen(false);
      else if (selected) choose(null);
      else if (controlsOpen) setControlsOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (catalogue.error) return <Page title="Body"><ErrorBox error={catalogue.error} /></Page>;
  if (!catalogue.data || !groups) return <Page title="Body"><PageSkeleton /></Page>;

  return (
    <div ref={stage} className="relative h-[calc(100svh-3.5rem)] min-h-[34rem] w-full overflow-hidden bg-background" data-testid="body-stage">
      <h1 className="sr-only">Body</h1>
      <div className="absolute inset-0">
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
            onAnchor={anchors.set}
            onStatus={(state, message) => setStatus({ state, message })}
          />
        </div>

        {status.state === 'loading' ? <div className="absolute inset-0 grid place-items-center text-sm text-muted-foreground" role="status">Loading the 3D body…</div> : null}
        {status.state === 'error' ? <div className="absolute inset-x-4 top-4 z-30 max-w-[calc(100%-18rem)]"><ErrorBox error={status.message ?? 'The 3D body could not be shown.'} /></div> : null}

        {current ? (
          <Callout
            group={current}
            body={body}
            fade={fade}
            exercises={library.data}
            libraryError={library.error}
            open={exercisesOpen}
            onToggle={() => setExercisesOpen((value) => !value)}
            onClose={() => choose(null)}
            store={anchors}
            stage={stage}
            rightInset={controlsOpen || listOpen ? RIGHT_INSET : TOGGLE_INSET}
          />
        ) : null}

        <div className="pointer-events-none absolute bottom-14 right-3 top-3 z-20 flex w-64 max-w-[calc(100%-1.5rem)] flex-col items-end gap-2">
          <Button
            variant={controlsOpen ? 'secondary' : 'outline'} size="icon" className="pointer-events-auto shrink-0 bg-card/90 shadow-lg backdrop-blur"
            aria-expanded={controlsOpen} aria-label={controlsOpen ? 'Close the controls' : 'Open the controls'} data-testid="controls-toggle"
            onClick={() => setControlsOpen((value) => !value)}
          >
            {controlsOpen ? <X /> : <Menu />}
          </Button>
          {controlsOpen ? (
            <div className="pointer-events-auto w-full space-y-3 rounded-xl border bg-card/90 p-3 shadow-lg backdrop-blur" data-testid="body-controls">
              <Button
                className="w-full" variant={listOpen ? 'secondary' : 'default'} aria-expanded={listOpen}
                aria-label={listOpen ? 'Close the muscle list' : 'Open the muscle list'} onClick={() => setListOpen((value) => !value)}
              >
                <List /> Muscles
              </Button>
              <FilterToggle label="Body" value={body} options={BODY_OPTIONS} onChange={setBody} />
              <div className="grid grid-cols-3 gap-1.5">
                <Button variant="outline" size="sm" className="px-2" onClick={() => setView({ side: 'front', n: (view?.n ?? 0) + 1 })}><ArrowRightToLine /> Front</Button>
                <Button variant="outline" size="sm" className="px-2" onClick={() => setView({ side: 'back', n: (view?.n ?? 0) + 1 })}><ArrowLeftToLine /> Back</Button>
                <Button variant="outline" size="sm" className="px-2" disabled={!selected && skin} onClick={() => choose(null)}><RotateCcw /> Reset</Button>
              </div>
              <div className="space-y-2">
                <Label className="flex items-center gap-2 text-sm">
                  <Switch checked={skin} onCheckedChange={setSkin} aria-label="Show the skin" /> <PersonStanding className="size-4 text-muted-foreground" aria-hidden="true" /> Skin
                </Label>
                <Label className="flex items-center gap-2 text-sm">
                  <Switch checked={fade} onCheckedChange={(on) => { setFade(on); setAutoFade(false); }} aria-label="Fade the surface muscles to see the deep ones" /> <Layers className="size-4 text-muted-foreground" aria-hidden="true" /> Fade surface
                </Label>
                <Label className="flex items-center gap-2 text-sm">
                  <Switch checked={skeleton} onCheckedChange={setSkeleton} aria-label="Show the skeleton" /> <span className="size-4" aria-hidden="true" /> Skeleton
                </Label>
              </div>
            </div>
          ) : null}
          {listOpen ? (
            <div className="pointer-events-auto min-h-0 w-full flex-1 overflow-y-auto rounded-xl border bg-card/95 shadow-lg backdrop-blur" data-testid="muscle-panel">
              <MuscleList groups={groups} hovered={hovered} onPick={(id) => choose({ id, side: 'C' })} onClose={() => setListOpen(false)} />
            </div>
          ) : null}
        </div>

        {hovered ? (
          <div className="pointer-events-none absolute bottom-3 left-1/2 z-10 -translate-x-1/2 rounded-md bg-background/85 px-2.5 py-1 text-sm font-medium shadow" data-testid="hover-label">{group(hovered)?.label ?? hovered}</div>
        ) : status.state === 'ready' && !selected ? (
          <div className="pointer-events-none absolute bottom-3 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-md bg-background/70 px-2.5 py-1 text-xs text-muted-foreground">Drag to turn · scroll to zoom · click a muscle</div>
        ) : null}
      <CreditsCorner open={creditsOpen} onToggle={() => setCreditsOpen((value) => !value)} />
    </div>
  );
}

// ---- The list of every muscle ----

function MuscleList({ groups, hovered, onPick, onClose }: { groups: MuscleGroup[]; hovered: string | null; onPick: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState('');
  const text = query.trim().toLowerCase();
  const sections = useMemo(() => MUSCLE_GROUPS
    .map((area) => ({ area, items: groups.filter((g) => g.libraryGroup === area && (!text || g.label.toLowerCase().includes(text) || g.about.toLowerCase().includes(text))).sort((a, b) => a.label.localeCompare(b.label)) }))
    .filter((section) => section.items.length), [groups, text]);
  return (
    <div className="space-y-3 p-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="text-base font-semibold">Muscles</h2>
          <p className="text-sm text-muted-foreground">Choose one, or click it on the body.</p>
        </div>
        <Button variant="ghost" size="icon" className="-mr-1 -mt-1 size-8" aria-label="Close the muscle list" onClick={onClose}><X /></Button>
      </div>
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
    </div>
  );
}

// ---- The floating box, and the line to the muscle ----

interface CalloutProps {
  group: MuscleGroup;
  body: BodyKey;
  fade: boolean;
  exercises: LibraryExercise[] | undefined;
  libraryError: unknown;
  open: boolean;
  onToggle: () => void;
  onClose: () => void;
  store: ReturnType<typeof createAnchorStore>;
  stage: React.RefObject<HTMLDivElement | null>;
  /** How much of the right edge of the stage is taken by the panels, which the box keeps out of. */
  rightInset: number;
}

/** Watches an element's size, so the box can be placed from how big the stage and the box are now. */
function useSize(element: React.RefObject<HTMLElement | null>, start: { width: number; height: number }) {
  const [size, setSize] = useState(start);
  useEffect(() => {
    const target = element.current;
    if (!target) return undefined;
    const read = () => setSize((old) => (old.width === target.offsetWidth && old.height === target.offsetHeight ? old : { width: target.offsetWidth, height: target.offsetHeight }));
    read();
    const observer = new ResizeObserver(read);
    observer.observe(target);
    return () => observer.disconnect();
  }, [element]);
  return size;
}

function Callout({ group, body, fade, exercises, libraryError, open, onToggle, onClose, store, stage, rightInset }: CalloutProps) {
  const anchor = useSyncExternalStore(store.subscribe, store.get, () => null);
  const card = useRef<HTMLDivElement>(null);
  const stageSize = useSize(stage, { width: 1000, height: 640 });
  const cardSize = useSize(card, { width: 352, height: 240 });
  const found = useMemo(() => (exercises ? exercisesForMuscle(exercises, group) : null), [exercises, group]);
  const layout = placeCallout(anchor, stageSize, cardSize, rightInset);
  return (
    <>
      {layout.line ? (
        <svg className="pointer-events-none absolute inset-0 z-10 size-full overflow-visible" aria-hidden="true" data-testid="callout-line">
          <line x1={layout.line.from.x} y1={layout.line.from.y} x2={layout.line.to.x} y2={layout.line.to.y} className="stroke-primary" strokeWidth="1.5" strokeLinecap="round" />
          <circle cx={layout.line.from.x} cy={layout.line.from.y} r="9" className="fill-primary/20 stroke-primary" strokeWidth="1" />
          <circle cx={layout.line.from.x} cy={layout.line.from.y} r="3.5" className="fill-primary" />
        </svg>
      ) : null}
      <div
        ref={card}
        className="absolute z-10 overflow-y-auto rounded-xl border bg-card/95 p-4 shadow-xl backdrop-blur"
        style={{ left: layout.left, top: layout.top, width: layout.width, maxHeight: maxCardHeight(stageSize) }}
        data-testid="muscle-callout"
      >
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1.5">
            <h2 className="text-xl font-semibold leading-tight" data-testid="muscle-title">{group.label}</h2>
            <div className="flex flex-wrap gap-1.5">
              <Badge variant="outline">{group.libraryGroup}</Badge>
              <Badge variant={group.layer === 'deep' ? 'secondary' : 'outline'}>{group.layer === 'deep' ? 'Deep muscle' : 'Surface muscle'}</Badge>
            </div>
          </div>
          <Button variant="ghost" size="icon" className="-mr-2 -mt-2 size-8 shrink-0" aria-label="Close the muscle card" onClick={onClose}><X /></Button>
        </div>
        <div className="mt-3 space-y-3">
          <p className="text-sm leading-relaxed">{group.about}</p>
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
        </div>
      </div>
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

/** The credits for the models, which their licence requires: a small button in the bottom right corner that opens them above it. */
function CreditsCorner({ open, onToggle }: { open: boolean; onToggle: () => void }) {
  return (
    <>
      {open ? (
        <div className="absolute bottom-14 right-3 z-30 w-[min(26rem,calc(100%-1.5rem))] space-y-2 rounded-xl border bg-card/95 p-4 text-sm text-muted-foreground shadow-xl backdrop-blur" data-testid="credits-panel">
          <div className="flex items-start justify-between gap-2">
            <h2 className="text-base font-semibold text-foreground">3D model credits</h2>
            <Button variant="ghost" size="icon" className="-mr-2 -mt-2 size-8 shrink-0" aria-label="Close the credits" onClick={onToggle}><X /></Button>
          </div>
          <p>
            Muscles and bones: BodyParts3D (© The Database Center for Life Science). Lats, abs, internal oblique, transverse abdominis, quadratus lumborum and
            multifidus: Z-Anatomy (Gauthier Kervyn, Marcin Zielinski), CC BY-SA 4.0. The female spine and the place of the bust contour in her skin: Human
            Reference Atlas / HuBMAP, CC BY 4.0.
          </p>
          <p>
            The models are shared under CC BY-SA 4.0: use and change them with this credit, and share changes under the same licence.{' '}
            <a className="text-primary underline underline-offset-2" href="/models/anatomy/ATTRIBUTION.md" target="_blank" rel="noreferrer noopener">Full credits and terms</a>. For
            education, not a clinical tool.
          </p>
        </div>
      ) : null}
      <Button
        variant="outline" size="sm" className="absolute bottom-3 right-3 z-30 bg-card/90 backdrop-blur" aria-expanded={open}
        aria-label={open ? 'Close the credits' : 'Open the credits'} onClick={onToggle} data-testid="credits-button"
      >
        <Info /> Credits
      </Button>
    </>
  );
}
