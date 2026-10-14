'use client';
// The 3D body, filling the whole page with the body in the middle and full control (turn, zoom), a skin layer over the muscles.
// Hover a muscle and it lights up; click it and the skin fades away, the muscle glows, the rest step back, and a text box floats in
// the air beside the body, joined to the muscle by a line, saying what it does, with the library exercises that train it
// (views/MuscleCard.tsx). An admin can edit the box: give the muscle a subtitle, rewrite its description, and add, move or remove its
// exercises. The controls are in the top right corner, behind a three-line button that opens the control panel (with a "Muscles"
// button that opens the list of every muscle, the way to reach one with the keyboard), and the credits for the models are a small
// button in the bottom right corner. Trainers use it to pick exercises for a client; the same catalogue and exercises feed the app.
import { ArrowLeftToLine, ArrowRightToLine, Info, Layers, List, Menu, PersonStanding, RotateCcw, X } from 'lucide-react';
import dynamic from 'next/dynamic';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import { useAdmin } from '@/components/AuthProvider';
import type { ViewerStatus } from '@/components/BodyViewer';
import { useFeedback } from '@/components/Feedback';
import { Modal } from '@/components/Modal';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, type FilterOption } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import type { PickedMuscle } from '@/lib/bodyScene';
import { createAnchorStore, maxCardHeight, placeCallout } from '@/lib/callout';
import { listLibrary, listMuscleInfo, resetMuscleInfo, saveLibraryExercise, saveMuscleInfo, setLibraryMuscles } from '@/lib/data';
import { useLoad } from '@/lib/hooks';
import { MUSCLE_GROUPS, slugify, withMuscleRole, type MuscleRole } from '@/lib/logic';
import { loadCatalogue, withMuscleInfo, type BodyKey, type MuscleGroup } from '@/lib/muscles';
import type { LibraryExercise, LibraryFields, LibraryInput } from '@/lib/types';
import { ExerciseForm } from './ExerciseForm';
import { MuscleCard } from './MuscleCard';

// three.js is large and only this page needs it: load it when the page opens, and never on the server.
const BodyViewer = dynamic(() => import('@/components/BodyViewer'), { ssr: false });

const BODY_OPTIONS: FilterOption<BodyKey>[] = [{ value: 'male', label: 'Male' }, { value: 'female', label: 'Female' }];
/** What the panels on the right take of the stage (their 16 rem and the gaps around it), which the floating box keeps out of; and what the three-line button alone takes. */
const RIGHT_INSET = 284;
const TOGGLE_INSET = 64;

const messageOf = (error: unknown): string => (error instanceof Error ? error.message : String(error));

export function BodyView() {
  const admin = useAdmin();
  const { toast } = useFeedback();
  const catalogue = useLoad(loadCatalogue);
  const library = useLoad(listLibrary);
  const muscleInfo = useLoad(listMuscleInfo);
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
  const [listOpen, setListOpen] = useState(false);
  const [creditsOpen, setCreditsOpen] = useState(false);
  /** The control panel behind the three-line button: closed to begin with, so the body has the whole view. */
  const [controlsOpen, setControlsOpen] = useState(false);
  /** The muscle a new exercise is being written for (the dialog is open), or null. */
  const [writingFor, setWritingFor] = useState<MuscleGroup | null>(null);

  const builtIn = catalogue.data?.groups;
  /** The muscles with what admins wrote about them (a subtitle, a description) put in. */
  const groups = useMemo(() => (builtIn ? withMuscleInfo(builtIn, muscleInfo.data) : undefined), [builtIn, muscleInfo.data]);
  const group = (id: string | null) => groups?.find((candidate) => candidate.id === id) ?? null;
  const current = group(selected?.id ?? null);

  /**
   * Picks a muscle (from the body or the list), or none. A picked muscle takes the skin off so it can be seen, and a deep one
   * also fades the surface muscles over it; picking none puts the skin back. The list closes when a muscle is picked from it.
   */
  function choose(next: PickedMuscle | null) {
    setSelected(next);
    setSkin(!next);
    if (next) setListOpen(false);
    const deep = next ? group(next.id)?.layer === 'deep' : false;
    if (deep && !fade) { setFade(true); setAutoFade(true); }
    if (!deep && autoFade) { setFade(false); setAutoFade(false); }
  }

  // ---- What an admin changes about a muscle ----

  async function saveInfo(id: string, value: { subtitle: string; about: string }, empty: boolean): Promise<string | undefined> {
    try {
      if (empty) await resetMuscleInfo(id); else await saveMuscleInfo(id, value, admin.uid);
    } catch (failure) {
      return `Could not save it: ${messageOf(failure)}`;
    }
    toast(empty ? 'Back to the built-in text' : 'Saved');
    muscleInfo.reload();
    return undefined;
  }

  async function resetInfo(id: string) {
    try {
      await resetMuscleInfo(id);
      toast('Back to the built-in text');
      muscleInfo.reload();
    } catch (failure) {
      toast(`Could not reset it: ${messageOf(failure)}`, 'error');
    }
  }

  /** Makes the muscle a main one, a helper or neither for a library exercise, and saves it to the library. */
  async function changeRole(muscle: MuscleGroup, exercise: LibraryExercise, role: MuscleRole) {
    const next = withMuscleRole(exercise, muscle.id, role);
    if (!next.ok) { toast(next.error, 'error'); return; }
    try {
      await setLibraryMuscles([{ id: exercise.id, primaryMuscles: next.primaryMuscles, secondaryMuscles: next.secondaryMuscles }], admin.uid);
    } catch (failure) {
      toast(`Could not change ${exercise.name}: ${messageOf(failure)}`, 'error');
      return;
    }
    toast(role === 'primary' ? `${exercise.name} is now a main exercise for ${muscle.label}`
      : role === 'secondary' ? `${exercise.name} now helps ${muscle.label}`
        : `${exercise.name} no longer trains ${muscle.label}`);
    library.reload();
  }

  /** Saves a new exercise from the dialog; it is already set to train the muscle it was written for. */
  async function createExercise(value: LibraryFields): Promise<string | undefined> {
    const id = slugify(value.name);
    if (library.data?.some((exercise) => exercise.id === id)) return 'An exercise with this name already exists.';
    try {
      await saveLibraryExercise(id, value, admin.uid, true);
    } catch (failure) {
      return messageOf(failure);
    }
    toast(`${value.name} added`);
    setWritingFor(null);
    library.reload();
    return undefined;
  }

  // Escape closes the credits first, then the muscle list, then the muscle's box, then the control panel.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || writingFor) return; // (a dialog takes its own Escape)
      // Escape while typing in the box (its edit form, the search) must not throw the box away with what was typed.
      if (event.target instanceof HTMLElement && ['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target.tagName)) return;
      if (creditsOpen) setCreditsOpen(false);
      else if (listOpen) setListOpen(false);
      else if (selected) choose(null);
      else if (controlsOpen) setControlsOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (catalogue.error) return <Page title="Body"><ErrorBox error={catalogue.error} /></Page>;
  if (!catalogue.data || !groups || !builtIn) return <Page title="Body"><PageSkeleton /></Page>;

  const hoveredGroup = group(hovered);

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
        <Callout store={anchors} stage={stage} rightInset={controlsOpen || listOpen ? RIGHT_INSET : TOGGLE_INSET}>
          <MuscleCard
            key={current.id}
            group={current}
            builtInAbout={builtIn.find((candidate) => candidate.id === current.id)?.about ?? current.about}
            body={body}
            fade={fade}
            library={library.data}
            libraryError={library.error}
            onClose={() => choose(null)}
            onSaveInfo={(value, empty) => saveInfo(current.id, value, empty)}
            onResetInfo={() => resetInfo(current.id)}
            onChangeRole={(exercise, role) => changeRole(current, exercise, role)}
            onNewExercise={() => setWritingFor(current)}
          />
        </Callout>
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

      {hoveredGroup ? (
        <div className="pointer-events-none absolute bottom-3 left-1/2 z-10 -translate-x-1/2 rounded-md bg-background/85 px-2.5 py-1 text-sm font-medium shadow" data-testid="hover-label">
          {hoveredGroup.label}{hoveredGroup.subtitle ? <span className="font-normal text-muted-foreground">{` · ${hoveredGroup.subtitle}`}</span> : null}
        </div>
      ) : status.state === 'ready' && !selected ? (
        <div className="pointer-events-none absolute bottom-3 left-1/2 z-10 -translate-x-1/2 whitespace-nowrap rounded-md bg-background/70 px-2.5 py-1 text-xs text-muted-foreground">Drag to turn · scroll to zoom · click a muscle</div>
      ) : null}
      <CreditsCorner open={creditsOpen} onToggle={() => setCreditsOpen((value) => !value)} />

      {writingFor ? (
        <Modal title={`New exercise for ${writingFor.label}`} onClose={() => setWritingFor(null)}>
          <ExerciseForm initial={newExerciseFor(writingFor)} onSave={createExercise} onCancel={() => setWritingFor(null)} />
        </Modal>
      ) : null}
    </div>
  );
}

/** A blank exercise that already trains [muscle] (as a main muscle) and sits in its library group. */
function newExerciseFor(muscle: MuscleGroup): LibraryInput {
  return {
    name: '', muscleGroup: MUSCLE_GROUPS.includes(muscle.libraryGroup) ? muscle.libraryGroup : 'Full body', equipment: '', level: 'Beginner', instructions: '', tips: '',
    defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true, primaryMuscles: [muscle.id], secondaryMuscles: [],
  };
}

// ---- The list of every muscle ----

function MuscleList({ groups, hovered, onPick, onClose }: { groups: MuscleGroup[]; hovered: string | null; onPick: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState('');
  const text = query.trim().toLowerCase();
  const sections = useMemo(() => MUSCLE_GROUPS
    .map((area) => ({
      area,
      items: groups
        .filter((g) => g.libraryGroup === area && (!text || g.label.toLowerCase().includes(text) || (g.subtitle ?? '').toLowerCase().includes(text) || g.about.toLowerCase().includes(text)))
        .sort((a, b) => a.label.localeCompare(b.label)),
    }))
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
                <Button variant={hovered === item.id ? 'secondary' : 'outline'} size="sm" onClick={() => onPick(item.id)} data-muscle={item.id} title={item.subtitle}>
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

/** Floats [children] (the muscle's card) in the air beside the body and draws the line from it to the picked muscle. */
function Callout({ store, stage, rightInset, children }: { store: ReturnType<typeof createAnchorStore>; stage: React.RefObject<HTMLDivElement | null>; rightInset: number; children: ReactNode }) {
  const anchor = useSyncExternalStore(store.subscribe, store.get, () => null);
  const card = useRef<HTMLDivElement>(null);
  const stageSize = useSize(stage, { width: 1000, height: 640 });
  const cardSize = useSize(card, { width: 352, height: 240 });
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
        {children}
      </div>
    </>
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
