import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { SKIN_OPACITY, blendCamera, bodyCamera, cameraFor, countExercises, exercisesForMuscle, isPickable, lightnessShift, lookOf, muscleLabel, withMuscleInfo, type Catalogue } from './muscles';
import type { LibraryExercise } from './types';

const root = join(__dirname, '..', '..', '..');
const catalogue = JSON.parse(readFileSync(join(root, 'models', 'anatomy', 'muscles.json'), 'utf8')) as Catalogue;
const seed = JSON.parse(readFileSync(join(root, 'admin', 'seed', 'library.json'), 'utf8')) as LibraryExercise[];

const exercise = (over: Partial<LibraryExercise>): LibraryExercise => ({
  id: 'x', name: 'X', muscleGroup: 'Back', equipment: '', level: '', instructions: '', tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true, ...over,
});

describe('the catalogue the 3D body is built from', () => {
  it('has every group with what the viewer needs, in both bodies', () => {
    expect(catalogue.groups.length).toBeGreaterThanOrEqual(34);
    for (const group of catalogue.groups) {
      expect(group.id, group.id).toMatch(/^[a-z]+(-[a-z]+)*$/);
      expect(group.label.length, group.id).toBeGreaterThan(0);
      expect(group.about.length, group.id).toBeGreaterThan(40);
      expect(['surface', 'deep'], group.id).toContain(group.layer);
      expect(['front', 'back', 'both'], group.id).toContain(group.view);
      for (const body of [group.male, group.female]) {
        expect(body?.nodes.length, group.id).toBeGreaterThan(0);
        expect(body?.box.max[1], group.id).toBeGreaterThan(body!.box.min[1]);
      }
    }
  });

  it('describes the two bodies, which are the same height scale and have the same muscles', () => {
    for (const key of ['male', 'female'] as const) {
      const info = catalogue.bodies[key];
      expect(info.file).toBe(`${key}.glb`);
      expect(info.height).toBeGreaterThan(1.5);
      expect(info.height).toBeLessThan(2);
    }
    for (const group of catalogue.groups) expect(group.male?.nodes.slice().sort(), group.id).toEqual(group.female?.nodes.slice().sort());
  });

  it('is what every library exercise in the starter set points at: each has main muscles, all of them real', () => {
    const ids = new Set(catalogue.groups.map((group) => group.id));
    for (const item of seed) {
      expect(item.primaryMuscles?.length, `${item.id} needs main muscles`).toBeGreaterThan(0);
      for (const id of [...(item.primaryMuscles ?? []), ...(item.secondaryMuscles ?? [])]) expect(ids.has(id), `${item.id}: unknown muscle ${id}`).toBe(true);
      // a muscle is a main one or a helper, not both
      expect((item.secondaryMuscles ?? []).filter((id) => item.primaryMuscles?.includes(id)), item.id).toEqual([]);
    }
  });

  it('leaves no muscle without an exercise that trains it, apart from the ones nobody lifts for', () => {
    const trained = new Set(seed.flatMap((item) => [...(item.primaryMuscles ?? []), ...(item.secondaryMuscles ?? [])]));
    const untrained = catalogue.groups.map((group) => group.id).filter((id) => !trained.has(id));
    expect(untrained.sort()).toEqual(['deep-back', 'feet', 'hands', 'hip-rotators', 'intercostals'].sort());
  });
});

describe('what admins wrote about the muscles', () => {
  const lats = () => catalogue.groups.find((group) => group.id === 'lats')!;

  it('puts a subtitle under a muscle and replaces its description with the admin\'s own', () => {
    const merged = withMuscleInfo(catalogue.groups, [{ id: 'lats', subtitle: ' Back wings ', about: 'Our own words.' }]);
    const changed = merged.find((group) => group.id === 'lats')!;
    expect(changed.subtitle).toBe('Back wings');
    expect(changed.about).toBe('Our own words.');
    expect(changed.edited).toBe(true);
    expect(changed.label).toBe(lats().label); // the name never changes: the body and the lists go by it
  });

  it('keeps the built-in description when the admin wrote only a subtitle, and the other way round', () => {
    const subtitleOnly = withMuscleInfo(catalogue.groups, [{ id: 'lats', subtitle: 'Wings', about: '' }]).find((group) => group.id === 'lats')!;
    expect(subtitleOnly.about).toBe(lats().about);
    expect(subtitleOnly.subtitle).toBe('Wings');
    const aboutOnly = withMuscleInfo(catalogue.groups, [{ id: 'lats', about: 'Words' }]).find((group) => group.id === 'lats')!;
    expect(aboutOnly.subtitle).toBeUndefined();
    expect(aboutOnly.about).toBe('Words');
  });

  it('leaves the other muscles, a note with nothing in it and a note about no muscle alone', () => {
    const merged = withMuscleInfo(catalogue.groups, [{ id: 'lats', subtitle: 'Wings' }, { id: 'chest', subtitle: '', about: '  ' }, { id: 'no-such-muscle', subtitle: 'x' }]);
    expect(merged.length).toBe(catalogue.groups.length);
    expect(merged.find((group) => group.id === 'chest')).toBe(catalogue.groups.find((group) => group.id === 'chest'));
    expect(merged.find((group) => group.id === 'biceps')).toBe(catalogue.groups.find((group) => group.id === 'biceps'));
    expect(merged.filter((group) => group.edited).map((group) => group.id)).toEqual(['lats']);
    expect(withMuscleInfo(catalogue.groups, undefined)).toBe(catalogue.groups);
    expect(withMuscleInfo(catalogue.groups, [])).toBe(catalogue.groups);
  });

  it('does not change the catalogue it was given', () => {
    const before = lats().about;
    withMuscleInfo(catalogue.groups, [{ id: 'lats', about: 'Changed' }]);
    expect(lats().about).toBe(before);
  });
});

describe('muscleLabel', () => {
  it('uses the catalogue, and makes a readable name for an id it does not know', () => {
    expect(muscleLabel(catalogue.groups, 'delt-front')).toBe('Front delts');
    expect(muscleLabel(catalogue.groups, 'some-new-muscle')).toBe('Some new muscle');
    expect(muscleLabel(undefined, 'lats')).toBe('Lats');
  });
});

describe('exercisesForMuscle', () => {
  const lats = { id: 'lats', libraryGroup: 'Back' };
  const list = [
    exercise({ id: 'pull-up', name: 'Pull-Up', primaryMuscles: ['lats'], secondaryMuscles: ['biceps'] }),
    exercise({ id: 'curl', name: 'Curl', muscleGroup: 'Biceps', primaryMuscles: ['biceps'], secondaryMuscles: ['lats'] }),
    exercise({ id: 'row', name: 'Row', primaryMuscles: ['mid-back'], secondaryMuscles: ['biceps'] }),
    exercise({ id: 'old', name: 'Old back exercise' }),
    exercise({ id: 'old-legs', name: 'Old leg exercise', muscleGroup: 'Legs' }),
    exercise({ id: 'hidden', name: 'Hidden', primaryMuscles: ['lats'], active: false }),
    exercise({ id: 'chin', name: 'Chin-Up', primaryMuscles: ['lats', 'biceps'] }),
  ];

  it('splits the exercises by how much they train the muscle', () => {
    const found = exercisesForMuscle(list, lats);
    expect(found.primary.map((e) => e.name)).toEqual(['Chin-Up', 'Pull-Up']);
    expect(found.secondary.map((e) => e.name)).toEqual(['Curl']);
    expect(countExercises(found)).toBe(4);
  });

  it('offers an exercise with no muscles set as "general" for its broad group only, and leaves hidden ones out', () => {
    const found = exercisesForMuscle(list, lats);
    expect(found.general.map((e) => e.name)).toEqual(['Old back exercise']);
    expect(exercisesForMuscle(list, { id: 'quadriceps', libraryGroup: 'Legs' }).general.map((e) => e.name)).toEqual(['Old leg exercise']);
    expect([...found.primary, ...found.secondary, ...found.general].some((e) => e.id === 'hidden')).toBe(false);
  });

  it('finds nothing for a muscle nobody trains', () => {
    expect(countExercises(exercisesForMuscle(list, { id: 'feet', libraryGroup: 'Full body' }))).toBe(0);
  });
});

describe('the camera', () => {
  const biceps = { min: [0.15, 1.15, -0.04] as [number, number, number], max: [0.29, 1.4, 0.04] as [number, number, number] };

  it('looks at the middle of a muscle from the side it is best seen from', () => {
    const front = cameraFor(biceps, 'front', { fov: 35, aspect: 1.5 });
    expect(front.target[0]).toBeCloseTo(0.22, 5);
    expect(front.target[1]).toBeCloseTo(1.275, 5);
    expect(front.position[2]).toBeGreaterThan(front.target[2]);
    const back = cameraFor(biceps, 'back', { fov: 35, aspect: 1.5 });
    expect(back.position[2]).toBeLessThan(back.target[2]);
    const side = cameraFor(biceps, 'both', { fov: 35, aspect: 1.5 });
    expect(Math.abs(side.position[0] - side.target[0])).toBeGreaterThan(Math.abs(side.position[2] - side.target[2]));
  });

  it('stands back far enough for a bigger muscle, and never closer than 45 cm', () => {
    const near = (box: typeof biceps) => { const c = cameraFor(box, 'front', { fov: 35, aspect: 1.5 }); return Math.hypot(...c.position.map((v, i) => v - c.target[i]!)); };
    const thigh = { min: [0.02, 0.45, -0.07] as [number, number, number], max: [0.2, 0.95, 0.09] as [number, number, number] };
    expect(near(thigh)).toBeGreaterThan(near(biceps));
    const tiny = { min: [0, 1, 0] as [number, number, number], max: [0.01, 1.01, 0.01] as [number, number, number] };
    expect(near(tiny)).toBeCloseTo(0.45, 5);
  });

  it('stands further back for a narrow view, so the muscle still fits', () => {
    const wide = cameraFor(biceps, 'front', { fov: 35, aspect: 2 });
    const narrow = cameraFor(biceps, 'front', { fov: 35, aspect: 0.5 });
    expect(Math.hypot(...narrow.position.map((v, i) => v - narrow.target[i]!))).toBeGreaterThan(Math.hypot(...wide.position.map((v, i) => v - wide.target[i]!)));
  });

  it('shows the whole body from straight in front, or straight behind', () => {
    const box = catalogue.bodies.male.box;
    const front = bodyCamera(box, 'front', { fov: 35, aspect: 1.2 });
    expect(front.position[0]).toBeCloseTo(front.target[0], 5);
    expect(front.position[2]).toBeGreaterThan(1.5);
    expect(bodyCamera(box, 'back', { fov: 35, aspect: 1.2 }).position[2]).toBeLessThan(-1.5);
  });

  it('fits the whole body in the view, with a margin, however wide or narrow the view is', () => {
    for (const key of ['male', 'female'] as const) {
      const box = catalogue.bodies[key].box;
      const fov = 35;
      for (const aspect of [0.5, 0.9, 1.6, 2.5]) {
        const { target, position } = bodyCamera(box, 'front', { fov, aspect });
        const distance = Math.hypot(...position.map((v, i) => v - target[i]!));
        const seenHeight = 2 * distance * Math.tan((fov * Math.PI) / 360); // how tall a slice the view shows at the body's distance
        const seenWidth = seenHeight * aspect;
        // the body's own height and width fit inside what the view shows (the near side of the body is closer, so this is cautious)
        expect(box.max[1] - box.min[1], `${key} height at aspect ${aspect}`).toBeLessThan(seenHeight);
        expect(box.max[0] - box.min[0], `${key} width at aspect ${aspect}`).toBeLessThan(seenWidth);
      }
    }
  });
});

describe('flying the camera from one view to another', () => {
  const body = catalogue.bodies.male.box;
  const spot = { fov: 35, aspect: 1.2 };
  const front = bodyCamera(body, 'front', spot);
  const back = bodyCamera(body, 'back', spot);
  const distance = (s: { target: number[]; position: number[] }) => Math.hypot(...s.position.map((v, i) => v - s.target[i]!));

  it('starts where it is and ends where it is going', () => {
    for (const [from, to] of [[front, back], [front, cameraFor(catalogue.groups.find((g) => g.id === 'lats')!.male!.box, 'back', spot)]] as const) {
      const start = blendCamera(from, to, 0), end = blendCamera(from, to, 1);
      start.position.forEach((v, i) => expect(v).toBeCloseTo(from.position[i]!, 5));
      end.position.forEach((v, i) => expect(v).toBeCloseTo(to.position[i]!, 5));
      end.target.forEach((v, i) => expect(v).toBeCloseTo(to.target[i]!, 5));
    }
  });

  it('goes round the body from the front to the back, never through it', () => {
    const lats = cameraFor(catalogue.groups.find((g) => g.id === 'lats')!.male!.box, 'back', spot);
    for (let k = 0; k <= 1.0001; k += 0.05) {
      const s = blendCamera(front, lats, k);
      // how far the camera is from the body's up-and-down axis: at least what the nearer end has, less a little for the aim moving
      const reach = Math.hypot(s.position[0] - 0, s.position[2] - 0);
      expect(reach, `at ${k.toFixed(2)}`).toBeGreaterThan(0.6);
      expect(distance(s), `at ${k.toFixed(2)}`).toBeGreaterThan(Math.min(distance(front), distance(lats)) * 0.9);
    }
    expect(Math.abs(blendCamera(front, back, 0.5).position[0]), 'halfway it is beside the body').toBeGreaterThan(distance(front) * 0.5);
  });

  it('takes the shorter way round, whichever side the muscle is on', () => {
    const right = { target: front.target, position: [front.target[0] + 2, front.target[1], front.target[2] + 0.2] as [number, number, number] };
    const left = { target: front.target, position: [front.target[0] - 2, front.target[1], front.target[2] + 0.2] as [number, number, number] };
    expect(blendCamera(front, right, 0.5).position[0]).toBeGreaterThan(front.target[0]);
    expect(blendCamera(front, left, 0.5).position[0]).toBeLessThan(front.target[0]);
  });
});

describe('how a muscle is drawn', () => {
  const surface = { id: 'lats', layer: 'surface' as const };
  const deep = { id: 'multifidus', layer: 'deep' as const };
  const none = { selected: null, hovered: null, fade: false };

  it('draws everything solid when nothing is picked', () => {
    expect(lookOf(surface, none)).toEqual({ opacity: 1, glow: 0 });
    expect(lookOf(deep, none)).toEqual({ opacity: 1, glow: 0 });
  });

  it('makes the picked muscle stand out and lets the others step back', () => {
    const state = { ...none, selected: 'lats' };
    expect(lookOf(surface, state)).toEqual({ opacity: 1, glow: 0.45 });
    expect(lookOf(deep, state).opacity).toBeLessThan(0.5);
    expect(lookOf({ id: 'chest', layer: 'surface' }, state).opacity).toBeLessThan(0.5);
  });

  it('fades the surface muscles on request, and fades them further when a deep one is picked', () => {
    const faded = { ...none, fade: true };
    expect(lookOf(surface, faded).opacity).toBeLessThan(0.3);
    expect(lookOf(deep, faded).opacity).toBe(1);
    const picked = { ...faded, selected: 'multifidus' };
    expect(lookOf(deep, picked).glow).toBeGreaterThan(0);
    expect(lookOf(surface, picked).opacity).toBeLessThan(lookOf(surface, faded).opacity);
  });

  it('glows a little under the pointer', () => {
    expect(lookOf(surface, { ...none, hovered: 'lats' }).glow).toBeGreaterThan(0);
    expect(lookOf(deep, { ...none, hovered: 'lats' }).glow).toBe(0);
  });

  it('can only click what can be seen', () => {
    expect(isPickable('surface', false)).toBe(true);
    expect(isPickable('surface', true)).toBe(false);
    expect(isPickable('deep', true)).toBe(true);
  });

  it('gives each muscle its own small, steady difference in lightness', () => {
    expect(lightnessShift('lats')).toBe(lightnessShift('lats'));
    const shifts = catalogue.groups.map((group) => lightnessShift(group.id));
    expect(Math.max(...shifts)).toBeLessThanOrEqual(0.06);
    expect(Math.min(...shifts)).toBeGreaterThanOrEqual(-0.06);
    expect(new Set(shifts).size).toBeGreaterThan(5);
  });

  it('keeps the skin see-through enough to show the muscles under it, and not so faint that it is no body', () => {
    expect(SKIN_OPACITY).toBeGreaterThan(0.2);
    expect(SKIN_OPACITY).toBeLessThan(0.7);
  });
});
