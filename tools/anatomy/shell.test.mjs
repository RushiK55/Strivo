import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bump, fillBody, frontSurface, sampleMeshes, surfaceNets } from './shell.mjs';

/** A closed sphere as a triangle mesh (latitude/longitude), enough to test the shell on a shape with a known inside. */
function sphere(radius, centre = [0, 0, 0], rings = 16, segments = 24) {
  const positions = [], indices = [];
  for (let r = 0; r <= rings; r++) for (let s = 0; s < segments; s++) {
    const phi = (r / rings) * Math.PI, theta = (s / segments) * 2 * Math.PI;
    positions.push(centre[0] + radius * Math.sin(phi) * Math.cos(theta), centre[1] + radius * Math.cos(phi), centre[2] + radius * Math.sin(phi) * Math.sin(theta));
  }
  for (let r = 0; r < rings; r++) for (let s = 0; s < segments; s++) {
    const a = r * segments + s, b = r * segments + ((s + 1) % segments), c = a + segments, d = b + segments;
    indices.push(a, c, b, b, c, d);
  }
  return { positions: Float32Array.from(positions), indices: Uint32Array.from(indices) };
}

/** Volume enclosed by a mesh (positive when the triangles face outward). */
function volume({ positions: p, indices: ix }) {
  let sum = 0;
  for (let t = 0; t < ix.length; t += 3) {
    const [a, b, c] = [ix[t] * 3, ix[t + 1] * 3, ix[t + 2] * 3];
    sum += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])) / 6;
  }
  return sum;
}

/** Every edge of a closed mesh is used by exactly two triangles. */
function isClosed({ indices }) {
  const edges = new Map();
  for (let t = 0; t < indices.length; t += 3) for (const [a, b] of [[0, 1], [1, 2], [2, 0]]) {
    const key = indices[t + a] < indices[t + b] ? `${indices[t + a]}-${indices[t + b]}` : `${indices[t + b]}-${indices[t + a]}`;
    edges.set(key, (edges.get(key) ?? 0) + 1);
  }
  return [...edges.values()].every((n) => n === 2);
}

test('the front surface is the highest point near each place, and nothing where there are no points', () => {
  const front = frontSurface(Float32Array.from([0, 0, 0.5, 0.004, 0, 0.7, 0.1, 0.1, 0.2]), [-0.1, 0.2, -0.1, 0.2]);
  assert.ok(Math.abs(front(0.002, 0) - 0.7) < 1e-6);
  assert.ok(Math.abs(front(0.1, 0.1) - 0.2) < 1e-6);
  assert.equal(front(0.15, -0.05), undefined);
  assert.equal(frontSurface(Float32Array.from([5, 5, 5]), [0, 1, 0, 1])(0, 0), undefined, 'points outside the box are ignored');
});

test('a bump rises to its height in the middle and lies on the surface at its edge, however the surface slopes', () => {
  const slope = (x) => 0.05 - x * 0.5; // a surface that falls away sideways
  const mesh = bump([0.1, 1.2], { wide: 0.06, high: 0.04, out: 0.02 }, (x) => slope(x));
  const p = mesh.positions;
  assert.ok(Math.abs(p[2] - (slope(0.1) + 0.02)) < 1e-6, 'the middle is at its full height above the surface');
  const last = (14 - 1) * 36 + 1; // the first vertex of the outer ring
  for (let k = 0; k < 36; k++) assert.ok(Math.abs(p[(last + k) * 3 + 2] - slope(p[(last + k) * 3])) < 1e-6, 'the outer ring is on the surface');
  const before = (14 - 2) * 36 + 1; // one ring in from the edge: about one percent of the height
  for (let k = 0; k < 36; k++) assert.ok(p[(before + k) * 3 + 2] - slope(p[(before + k) * 3]) < 0.02 * 0.03);
  let wideReach = 0, highReach = 0;
  for (let i = 0; i < p.length; i += 3) { wideReach = Math.max(wideReach, Math.abs(p[i] - 0.1)); highReach = Math.max(highReach, Math.abs(p[i + 1] - 1.2)); }
  assert.ok(Math.abs(wideReach - 0.06) < 1e-3 && Math.abs(highReach - 0.04) < 1e-3, 'it spans the half-width and half-height');
  for (const index of mesh.indices) assert.ok(index < p.length / 3);
});

test('a bump leaves out the places with no surface under them, and refuses to be made with none in the middle', () => {
  const partial = bump([0, 0], { wide: 0.05, high: 0.05, out: 0.01 }, (x) => (x < 0.02 ? 0 : undefined));
  assert.ok(partial.positions.length > 0 && partial.indices.length > 0);
  for (let i = 0; i < partial.positions.length; i += 3) assert.ok(partial.positions[i] < 0.02 + 1e-9);
  assert.throws(() => bump([0, 0], { wide: 0.05, high: 0.05, out: 0.01 }, () => undefined), /no surface/);
});

const settings = { voxel: 0.01, grow: 1.5, radius: 1, passes: 2 };

test('sampling puts points over the whole surface', () => {
  const points = sampleMeshes([sphere(0.1)], 0.01);
  assert.ok(points.length / 3 > 500);
  for (let i = 0; i < points.length; i += 3) assert.ok(Math.abs(Math.hypot(points[i], points[i + 1], points[i + 2]) - 0.1) < 0.005);
});

test('the shell of a sphere is a closed surface facing outward, close to the sphere (a little bigger)', () => {
  const mesh = surfaceNets(fillBody(sampleMeshes([sphere(0.1)], 0.004), settings));
  assert.ok(isClosed(mesh), 'every edge is shared by two triangles');
  const v = volume(mesh);
  const sphereVolume = (4 / 3) * Math.PI * 0.1 ** 3;
  assert.ok(v > sphereVolume && v < sphereVolume * 1.8, `volume ${v} against ${sphereVolume}`);
});

test('a hollow shell is filled in, so there is no inner surface', () => {
  const mesh = surfaceNets(fillBody(sampleMeshes([sphere(0.1)], 0.004), settings));
  const radii = [];
  for (let i = 0; i < mesh.positions.length; i += 3) radii.push(Math.hypot(mesh.positions[i], mesh.positions[i + 1], mesh.positions[i + 2]));
  assert.ok(Math.min(...radii) > 0.09, 'no vertex inside the sphere');
});

test('two parts a hair apart are joined into one body', () => {
  const points = sampleMeshes([sphere(0.05, [-0.052, 0, 0]), sphere(0.05, [0.052, 0, 0])], 0.004);
  const mesh = surfaceNets(fillBody(points, settings));
  assert.ok(isClosed(mesh));
  let near = 0;
  for (let i = 0; i < mesh.positions.length; i += 3) if (Math.abs(mesh.positions[i]) < 0.004) near++;
  assert.ok(near > 20, 'a bridge between the two');
});

test('closing fills a hollow between two parts of the head that plain growing leaves open', () => {
  const points = sampleMeshes([sphere(0.05, [-0.065, 0, 0]), sphere(0.05, [0.065, 0, 0])], 0.004); // 30 mm apart
  const crossing = (closing) => {
    const { positions } = surfaceNets(fillBody(points, { voxel: 0.005, grow: 1, radius: 1, passes: 1, closing }));
    let near = 0;
    for (let i = 0; i < positions.length; i += 3) if (Math.abs(positions[i]) < 0.004) near++;
    return near;
  };
  assert.equal(crossing(undefined), 0, 'two separate bodies');
  assert.ok(crossing({ minY: -1, radius: 5 }) > 20, 'joined by closing');
  assert.equal(crossing({ minY: 1, radius: 5 }), 0, 'closing does nothing below its height');
});
