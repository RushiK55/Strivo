import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DisplacementField, matchMuscles } from './warp.mjs';

/** A cloud of points on a grid, as Float32Array x y z. */
const grid = (n, step, origin = [0, 0, 0]) => {
  const out = [];
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) for (let k = 0; k < n; k++) out.push(origin[0] + i * step, origin[1] + j * step, origin[2] + k * step);
  return new Float32Array(out);
};
const shifted = (positions, by) => positions.map((v, i) => v + by[i % 3]);

test('a point is moved by the way its neighbours moved', () => {
  const male = grid(6, 0.01);
  const field = new DisplacementField([{ male, female: shifted(male, [0.05, -0.02, 0.01]) }]);
  const [dx, dy, dz] = field.at(0.025, 0.025, 0.025);
  assert.ok(Math.abs(dx - 0.05) < 1e-6 && Math.abs(dy + 0.02) < 1e-6 && Math.abs(dz - 0.01) < 1e-6);
});

test('apply moves every vertex of a mesh and leaves the original alone', () => {
  const male = grid(6, 0.01);
  const field = new DisplacementField([{ male, female: shifted(male, [0, 0.1, 0]) }]);
  const mesh = new Float32Array([0.02, 0.02, 0.02, 0.04, 0.03, 0.01]);
  const moved = field.apply(mesh);
  assert.ok(Math.abs(moved[1] - 0.12) < 1e-5 && Math.abs(moved[4] - 0.13) < 1e-5);
  assert.deepEqual([...mesh], [0.02, 0.02, 0.02, 0.04, 0.03, 0.01].map((v) => Math.fround(v)));
});

test('two regions that move differently each give their own movement', () => {
  const left = grid(5, 0.01), right = grid(5, 0.01, [0.5, 0, 0]);
  const field = new DisplacementField([{ male: left, female: shifted(left, [0.1, 0, 0]) }, { male: right, female: shifted(right, [-0.1, 0, 0]) }]);
  assert.ok(Math.abs(field.at(0.02, 0.02, 0.02)[0] - 0.1) < 1e-5);
  assert.ok(Math.abs(field.at(0.52, 0.02, 0.02)[0] + 0.1) < 1e-5);
});

test('a part can be left out, to test how well the others predict it', () => {
  const a = grid(5, 0.01), b = grid(5, 0.01, [0.06, 0, 0]);
  const field = new DisplacementField([{ male: a, female: shifted(a, [1, 0, 0]) }, { male: b, female: shifted(b, [2, 0, 0]) }]);
  assert.ok(Math.abs(field.at(0.02, 0.02, 0.02, { skipOwner: 0 })[0] - 2) < 1e-5);
  assert.ok(Math.abs(field.at(0.02, 0.02, 0.02)[0] - 1) < 0.2);
});

test('a point far from everything gets the nearest movement, and an empty field moves nothing', () => {
  const male = grid(3, 0.01);
  const field = new DisplacementField([{ male, female: shifted(male, [0.3, 0, 0]) }]);
  assert.ok(Math.abs(field.at(0.3, 0.3, 0.3)[0] - 0.3) < 1e-5);
  assert.deepEqual(new DisplacementField([]).at(0, 0, 0), [0, 0, 0]);
});

test('muscles are paired by name and by side, and only when their vertices correspond', () => {
  const part = (name, x, vertices = 3) => ({ name, positions: new Float32Array(Array.from({ length: vertices * 3 }, (_, i) => (i % 3 === 0 ? x : 0))) });
  const centreX = (p) => p.positions[0];
  const male = [part('External intercostal muscle', 0.1), part('external intercostal muscle', -0.1), part('Right biceps', -0.2), part('Left biceps', 0.2, 4)];
  const female = [part('external intercostal muscle', 0.12), part('External intercostal muscle', -0.12), part('Right biceps', -0.18), part('Left biceps', 0.18, 3)];
  const pairs = matchMuscles(male, female, centreX);
  // the two intercostals pair left with left, right with right, even though the names differ in capitals
  const intercostal = pairs.filter((p) => /intercostal/i.test(p.name));
  assert.equal(intercostal.length, 2);
  assert.ok(intercostal.every((p) => Math.sign(p.male[0]) === Math.sign(p.female[0])));
  // the left biceps has a different vertex count in the two bodies, so it is not used
  assert.deepEqual(pairs.filter((p) => /biceps/i.test(p.name)).map((p) => p.name), ['Right biceps']);
});
