// The muscle catalogue the 3D body is built from (models/anatomy/muscles.json) and the pure rules around it: which library
// exercises train a muscle, where the camera goes to look at one, and how a muscle is drawn when it is hovered or selected.
// No React and no three.js in here, so it is all covered by muscles.test.ts.
import type { LibraryExercise, MuscleInfo } from './types';

export type Vec3 = [number, number, number];
export interface Box { min: Vec3; max: Vec3 }
export type BodyKey = 'male' | 'female';
export type MuscleView = 'front' | 'back' | 'both';
export type MuscleLayer = 'surface' | 'deep';

export interface MuscleBody { nodes: string[]; box: Box; sides: Record<string, Box>; triangles: number }

export interface MuscleGroup {
  id: string;
  label: string;
  /** What the muscle is and does, in a sentence or two. */
  about: string;
  /** The exercise library's broad group (Chest, Back, Legs ...) this muscle belongs to. */
  libraryGroup: string;
  /** The side the camera shows when it zooms in. */
  view: MuscleView;
  /** "deep" muscles lie under the surface ones and can only be seen when those are faded. */
  layer: MuscleLayer;
  /** A friendlier name an admin gave it, shown under its name. */
  subtitle?: string;
  /** True when an admin has written something about it (a subtitle or a description of their own). */
  edited?: boolean;
  male?: MuscleBody;
  female?: MuscleBody;
}

export interface Catalogue {
  version: number;
  bodies: Record<BodyKey, { file: string; bytes: number; height: number; box: Box; triangles: number }>;
  groups: MuscleGroup[];
}

/** Where the models are served from (copied from models/anatomy at build time, see scripts/copy-models.mjs). */
export const MODELS_URL = '/models/anatomy/';

let catalogue: Promise<Catalogue> | null = null;

/** Reads muscles.json once; a failed read is not remembered, so the next call tries again. */
export function loadCatalogue(): Promise<Catalogue> {
  catalogue ??= fetch(`${MODELS_URL}muscles.json`)
    .then((response) => {
      if (!response.ok) throw new Error(`The muscle catalogue could not be loaded (${response.status}).`);
      return response.json() as Promise<Catalogue>;
    })
    .then((data) => {
      if (!Array.isArray(data.groups) || !data.bodies) throw new Error('The muscle catalogue is not in the expected format.');
      return data;
    })
    .catch((error: unknown) => { catalogue = null; throw error; });
  return catalogue;
}

/** "delt-front" -> "Front delts" from the catalogue, or a readable fallback ("Delt front") when it is not known. */
export function muscleLabel(groups: MuscleGroup[] | undefined, id: string): string {
  const found = groups?.find((group) => group.id === id);
  if (found) return found.label;
  const text = id.replace(/-/g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * The catalogue's muscles with what admins wrote about them: the subtitle, and a description of their own in place of the built-in
 * one when they wrote it. A muscle nobody wrote about is returned as it is. `label` never changes (the 3D body and the exercise
 * lists go by it).
 */
export function withMuscleInfo(groups: MuscleGroup[], infos: MuscleInfo[] | undefined): MuscleGroup[] {
  if (!infos?.length) return groups;
  const byId = new Map(infos.map((info) => [info.id, info]));
  return groups.map((group) => {
    const info = byId.get(group.id);
    if (!info) return group;
    const subtitle = info.subtitle?.trim();
    const about = info.about?.trim();
    if (!subtitle && !about) return group;
    return { ...group, ...(subtitle ? { subtitle } : {}), ...(about ? { about } : {}), edited: true };
  });
}

export interface ExercisesForMuscle {
  /** The exercise trains this muscle mainly. */
  primary: LibraryExercise[];
  /** It works the muscle as a helper. */
  secondary: LibraryExercise[];
  /** No exact muscles are set on it yet, but it is in the same broad group (Back for the lats). */
  general: LibraryExercise[];
}

const byName = (a: LibraryExercise, b: LibraryExercise) => a.name.localeCompare(b.name);

/** The shown library exercises for one muscle, split by how much they train it. Hidden exercises are left out. */
export function exercisesForMuscle(exercises: LibraryExercise[], group: Pick<MuscleGroup, 'id' | 'libraryGroup'>): ExercisesForMuscle {
  const shown = exercises.filter((exercise) => exercise.active);
  const hasMuscles = (exercise: LibraryExercise) => (exercise.primaryMuscles?.length ?? 0) > 0;
  return {
    primary: shown.filter((e) => e.primaryMuscles?.includes(group.id)).sort(byName),
    secondary: shown.filter((e) => !e.primaryMuscles?.includes(group.id) && e.secondaryMuscles?.includes(group.id)).sort(byName),
    general: shown.filter((e) => !hasMuscles(e) && e.muscleGroup === group.libraryGroup).sort(byName),
  };
}

export const countExercises = (found: ExercisesForMuscle): number => found.primary.length + found.secondary.length + found.general.length;

// ---- The camera ----

export interface CameraSpot { target: Vec3; position: Vec3 }

/** Which way the camera looks at a muscle from, as a direction from the muscle to the camera. */
const DIRECTIONS: Record<MuscleView, Vec3> = {
  front: [0.22, 0.1, 1],
  back: [-0.22, 0.1, -1],
  both: [1, 0.12, 0.55],
};

const normalise = (v: Vec3): Vec3 => { const l = Math.hypot(...v) || 1; return [v[0] / l, v[1] / l, v[2] / l]; };

/**
 * Where the camera goes to look at [box]: far enough back for all of it to fit with some air around it, from the side the muscle
 * is best seen from. [fov] is the vertical field of view in degrees and [aspect] the width over the height of the view.
 */
export function cameraFor(box: Box, view: MuscleView, { fov, aspect }: { fov: number; aspect: number }): CameraSpot {
  const target: Vec3 = [0, 1, 2].map((a) => (box.min[a]! + box.max[a]!) / 2) as Vec3;
  const radius = Math.hypot(box.max[0] - box.min[0], box.max[1] - box.min[1], box.max[2] - box.min[2]) / 2;
  const vertical = (fov * Math.PI) / 180 / 2;
  const horizontal = Math.atan(Math.tan(vertical) * Math.max(aspect, 0.2));
  const distance = Math.max((radius * 1.6) / Math.sin(Math.min(vertical, horizontal)), 0.45);
  const dir = normalise(DIRECTIONS[view]);
  return { target, position: [target[0] + dir[0] * distance, target[1] + dir[1] * distance, target[2] + dir[2] * distance] };
}

/**
 * The whole body from straight in front (or behind), as the starting view: far enough back for its height (or, in a narrow
 * view, its width) to fit with a margin, and looking at its middle.
 */
export function bodyCamera(box: Box, side: 'front' | 'back', { fov, aspect }: { fov: number; aspect: number }): CameraSpot {
  const target: Vec3 = [0, 1, 2].map((a) => (box.min[a]! + box.max[a]!) / 2) as Vec3;
  const half = (a: number) => (box.max[a]! - box.min[a]!) / 2;
  const tan = Math.tan((fov * Math.PI) / 180 / 2);
  const distance = Math.max(half(1) / tan, half(0) / (tan * Math.max(aspect, 0.2))) * 1.1 + half(2);
  const dir = normalise(side === 'front' ? [0, 0.03, 1] : [0, 0.03, -1]);
  return { target, position: [target[0] + dir[0] * distance, target[1] + dir[1] * distance, target[2] + dir[2] * distance] };
}

/**
 * The camera [k] of the way (0 to 1) from [from] to [to]: the point it looks at moves in a straight line, but the camera swings
 * round it (the shorter way, keeping its height angle and distance changing smoothly) instead of cutting through the body, as
 * it would going from the front to a muscle on the back.
 */
export function blendCamera(from: CameraSpot, to: CameraSpot, k: number): CameraSpot {
  const mix = (a: number, b: number) => a + (b - a) * k;
  const target: Vec3 = [mix(from.target[0], to.target[0]), mix(from.target[1], to.target[1]), mix(from.target[2], to.target[2])];
  const offset = (spot: CameraSpot): Vec3 => [spot.position[0] - spot.target[0], spot.position[1] - spot.target[1], spot.position[2] - spot.target[2]];
  const a = offset(from), b = offset(to);
  const la = Math.hypot(...a), lb = Math.hypot(...b);
  const turn = (v: Vec3) => Math.atan2(v[0], v[2]);
  const lift = (v: Vec3, l: number) => Math.asin(Math.max(-1, Math.min(1, v[1] / (l || 1))));
  let swing = turn(b) - turn(a);
  swing = ((((swing + Math.PI) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)) - Math.PI; // the shorter way round, -180 to 180 degrees
  const around = turn(a) + swing * k;
  const up = mix(lift(a, la), lift(b, lb));
  const distance = mix(la, lb);
  return {
    target,
    position: [
      target[0] + distance * Math.sin(around) * Math.cos(up),
      target[1] + distance * Math.sin(up),
      target[2] + distance * Math.cos(around) * Math.cos(up),
    ],
  };
}

// ---- How a muscle is drawn ----

export interface DrawState { selected: string | null; hovered: string | null; fade: boolean }
export interface Look { opacity: number; /** 0 = no glow */ glow: number }

/**
 * How one muscle looks: the selected one is solid and glows; the others step back so it stands out (more so when the surface
 * layer is faded and the muscle is a surface one); the hovered one glows a little. With nothing selected the surface layer
 * is solid, or faint when faded, and the deep layer is solid.
 */
export function lookOf(group: Pick<MuscleGroup, 'id' | 'layer'>, state: DrawState): Look {
  const faded = state.fade && group.layer === 'surface';
  if (state.selected === group.id) return { opacity: 1, glow: 0.45 };
  const hover = state.hovered === group.id ? 0.22 : 0;
  if (state.selected) return { opacity: faded ? 0.07 : 0.3, glow: hover };
  return { opacity: faded ? 0.14 : 1, glow: hover };
}

/** A muscle can be clicked when it can be seen: with the surface faded only the deep muscles can. */
export const isPickable = (layer: MuscleLayer, fade: boolean): boolean => !(fade && layer === 'surface');

/** A small steady difference in lightness per muscle (-0.06 to +0.06), so neighbours of one colour can still be told apart. */
export function lightnessShift(id: string): number {
  let h = 0;
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return ((h % 13) / 12 - 0.5) * 0.12;
}

// ---- The skin ----

/** How solid the skin layer is when it is on: see-through enough for the muscles to show, solid enough to read as a body. */
export const SKIN_OPACITY = 0.42;
