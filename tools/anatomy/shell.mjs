// The skin layer: a smooth, skin-like shell around the muscles and bones, drawn over the muscles in the viewers.
// Plain functions on typed arrays, tested in shell.test.mjs.
//
//   1. sampleMeshes   scatters points over the surfaces of the muscles and bones
//   2. fillBody       puts the points in a voxel grid, grows them a little (skin and fat), fills the inside and blurs the result
//   3. surfaceNets    turns that blurred volume into a smooth triangle mesh

// ---- 1. Points on the surfaces ----

/** Points about [spacing] apart over the triangles of [meshes] ({ positions, indices }), as x y z. */
export function sampleMeshes(meshes, spacing) {
  const xs = [];
  for (const { positions: p, indices: ix } of meshes) {
    for (let t = 0; t < ix.length; t += 3) {
      const a = ix[t] * 3, b = ix[t + 1] * 3, c = ix[t + 2] * 3;
      const longest = Math.max(
        Math.hypot(p[b] - p[a], p[b + 1] - p[a + 1], p[b + 2] - p[a + 2]),
        Math.hypot(p[c] - p[a], p[c + 1] - p[a + 1], p[c + 2] - p[a + 2]),
        Math.hypot(p[c] - p[b], p[c + 1] - p[b + 1], p[c + 2] - p[b + 2]));
      const n = Math.max(1, Math.ceil(longest / spacing));
      for (let i = 0; i <= n; i++) for (let j = 0; i + j <= n; j++) {
        const u = i / n, v = j / n, w = 1 - u - v;
        xs.push(p[a] * w + p[b] * u + p[c] * v, p[a + 1] * w + p[b + 1] * u + p[c + 1] * v, p[a + 2] * w + p[b + 2] * u + p[c + 2] * v);
      }
    }
  }
  return Float32Array.from(xs);
}

/**
 * The front of the body as a function of (x, y): the highest z among the sample [points] (x y z) within [reach] sideways, or
 * undefined where there are none. Built over the box [x0, x1] by [y0, y1] only. It follows the body's real front surface, which
 * curves away from any plane.
 */
export function frontSurface(points, [x0, x1, y0, y1], reach = 0.006) {
  const cell = reach;
  const key = (cx, cy) => cx * 100_003 + cy;
  const tops = new Map();
  for (let i = 0; i < points.length; i += 3) {
    const x = points[i], y = points[i + 1];
    if (x < x0 - reach || x > x1 + reach || y < y0 - reach || y > y1 + reach) continue;
    const k = key(Math.floor(x / cell), Math.floor(y / cell));
    if (!(tops.get(k) >= points[i + 2])) tops.set(k, points[i + 2]);
  }
  return (x, y) => {
    let best;
    const cx = Math.floor(x / cell), cy = Math.floor(y / cell);
    for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) {
      const z = tops.get(key(cx + dx, cy + dy));
      if (z !== undefined && (best === undefined || z > best)) best = z;
    }
    return best;
  };
}

/**
 * A smooth bump over an elliptical patch centred on ([x], [y]), half-width [wide] and half-height [high]: a sheet that lies on
 * the surface given by [surface] (x, y) => z at its edge and rises to [out] above it in the middle, the height following
 * (1 + cos) / 2 of the way out so it melts into the surface with no step. Used for the soft bust contour of the female skin,
 * where the sheet only has to shape the skin that is grown around it. A point with no surface under it is left out.
 */
export function bump([x, y], { wide, high, out }, surface, rings = 14, segments = 36) {
  const positions = [], indices = [], at = new Map();
  const add = (px, py, lift) => {
    const base = surface(px, py);
    if (base === undefined) return undefined;
    positions.push(px, py, base + lift);
    return positions.length / 3 - 1;
  };
  const centre = add(x, y, out);
  if (centre === undefined) throw new Error('there is no surface under the middle of the bump');
  for (let r = 1; r <= rings; r++) {
    const rho = r / rings;
    const lift = out * (1 + Math.cos(Math.PI * rho)) / 2;
    for (let k = 0; k < segments; k++) {
      const theta = (k / segments) * 2 * Math.PI;
      at.set(r * segments + k, add(x + wide * rho * Math.cos(theta), y + high * rho * Math.sin(theta), lift));
    }
  }
  const vertex = (r, k) => at.get(r * segments + (k % segments));
  const triangle = (p, q, w) => { if (p !== undefined && q !== undefined && w !== undefined) indices.push(p, q, w); };
  for (let k = 0; k < segments; k++) triangle(centre, vertex(1, k), vertex(1, k + 1));
  for (let r = 1; r < rings; r++) for (let k = 0; k < segments; k++) {
    triangle(vertex(r, k), vertex(r + 1, k), vertex(r + 1, k + 1));
    triangle(vertex(r, k), vertex(r + 1, k + 1), vertex(r, k + 1));
  }
  return { positions: Float32Array.from(positions), indices: Uint32Array.from(indices) };
}

// ---- 2. The body as a volume ----

/**
 * A grid of 0 (outside) to 1 (inside) over the body: every grid cell touched by a point is grown by [grow] cells (so the gaps
 * between muscles close and the shell sits a little outside them), the outside is found by flooding in from the corner so
 * that everything enclosed counts as inside, and the result is blurred [passes] times over [radius] cells to round it off.
 * With `closing` ({ minY, radius }), above height minY every hollow narrower than twice `radius` cells (the eye sockets, the
 * mouth) is filled first, so the head comes out as a smooth shape instead of a skull.
 */
export function fillBody(points, { voxel, grow, radius, passes, closing }) {
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < points.length; i += 3) for (let a = 0; a < 3; a++) {
    lo[a] = Math.min(lo[a], points[i + a]); hi[a] = Math.max(hi[a], points[i + a]);
  }
  const margin = (Math.ceil(grow) + radius * passes + 3) * voxel;
  const origin = lo.map((v) => v - margin);
  const dims = hi.map((v, a) => Math.ceil((v + margin - origin[a]) / voxel) + 1);
  const [nx, ny, nz] = dims;
  const count = nx * ny * nz;

  const touched = new Uint8Array(count);
  for (let i = 0; i < points.length; i += 3) {
    const x = Math.round((points[i] - origin[0]) / voxel), y = Math.round((points[i + 1] - origin[1]) / voxel), z = Math.round((points[i + 2] - origin[2]) / voxel);
    touched[(z * ny + y) * nx + x] = 1;
  }

  if (closing) closeAbove(touched, dims, Math.floor((closing.minY - origin[1]) / voxel), closing.radius);

  const reach = Math.ceil(grow);
  const ball = [];
  for (let dz = -reach; dz <= reach; dz++) for (let dy = -reach; dy <= reach; dy++) for (let dx = -reach; dx <= reach; dx++) {
    if (dx * dx + dy * dy + dz * dz <= grow * grow) ball.push([dx, dy, dz]);
  }
  const solid = new Uint8Array(count);
  for (let z = 0; z < nz; z++) for (let y = 0; y < ny; y++) for (let x = 0; x < nx; x++) {
    if (!touched[(z * ny + y) * nx + x]) continue;
    for (const [dx, dy, dz] of ball) {
      const X = x + dx, Y = y + dy, Z = z + dz;
      if (X >= 0 && Y >= 0 && Z >= 0 && X < nx && Y < ny && Z < nz) solid[(Z * ny + Y) * nx + X] = 1;
    }
  }

  // The outside: everything the corner can reach without crossing a solid cell.
  const outside = new Uint8Array(count);
  const queue = new Int32Array(count);
  let head = 0, tail = 0;
  outside[0] = 1; queue[tail++] = 0;
  while (head < tail) {
    const at = queue[head++];
    const x = at % nx, y = Math.floor(at / nx) % ny, z = Math.floor(at / (nx * ny));
    const next = [x > 0 ? at - 1 : -1, x < nx - 1 ? at + 1 : -1, y > 0 ? at - nx : -1, y < ny - 1 ? at + nx : -1, z > 0 ? at - nx * ny : -1, z < nz - 1 ? at + nx * ny : -1];
    for (const to of next) if (to >= 0 && !outside[to] && !solid[to]) { outside[to] = 1; queue[tail++] = to; }
  }

  let field = new Float32Array(count);
  for (let i = 0; i < count; i++) field[i] = outside[i] ? 0 : 1;
  for (let pass = 0; pass < passes; pass++) field = blur(field, dims, radius);
  return { field, dims, origin, voxel };
}

/** Morphological closing (grow by [r] cells, then shrink by [r]) of the cells at or above row [fromY], in place. */
function closeAbove(cells, [nx, ny, nz], fromY, r) {
  const offsets = [];
  for (let dz = -r; dz <= r; dz++) for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
    if (dx * dx + dy * dy + dz * dz <= r * r) offsets.push([dx, dy, dz]);
  }
  const low = Math.max(fromY - r, 0);
  const grown = new Uint8Array(cells.length);
  for (let z = 0; z < nz; z++) for (let y = Math.max(fromY, 0); y < ny; y++) for (let x = 0; x < nx; x++) {
    if (!cells[(z * ny + y) * nx + x]) continue;
    for (const [dx, dy, dz] of offsets) {
      const X = x + dx, Y = y + dy, Z = z + dz;
      if (X >= 0 && Y >= low && Z >= 0 && X < nx && Y < ny && Z < nz) grown[(Z * ny + Y) * nx + X] = 1;
    }
  }
  for (let z = 0; z < nz; z++) for (let y = Math.max(fromY, 0); y < ny; y++) for (let x = 0; x < nx; x++) {
    const at = (z * ny + y) * nx + x;
    if (!grown[at] || cells[at]) continue;
    let kept = true;
    for (const [dx, dy, dz] of offsets) {
      const X = x + dx, Y = y + dy, Z = z + dz;
      if (X < 0 || Y < 0 || Z < 0 || X >= nx || Y >= ny || Z >= nz || !grown[(Z * ny + Y) * nx + X]) { kept = false; break; }
    }
    if (kept) cells[at] = 1;
  }
}

/** One box blur of a grid, along each axis in turn (the same as a blur in 3D, at a third of the work). */
function blur(field, [nx, ny, nz], radius) {
  let source = field;
  const axes = [
    { length: nx, step: 1, lines: ny * nz, lineStep: (i) => Math.floor(i / ny) * nx * ny + (i % ny) * nx },
    { length: ny, step: nx, lines: nx * nz, lineStep: (i) => Math.floor(i / nx) * nx * ny + (i % nx) },
    { length: nz, step: nx * ny, lines: nx * ny, lineStep: (i) => i },
  ];
  for (const { length, step, lines, lineStep } of axes) {
    const target = new Float32Array(source.length);
    for (let line = 0; line < lines; line++) {
      const start = lineStep(line);
      let sum = 0;
      for (let i = -radius; i <= radius; i++) sum += source[start + Math.min(Math.max(i, 0), length - 1) * step];
      const width = radius * 2 + 1;
      for (let i = 0; i < length; i++) {
        target[start + i * step] = sum / width;
        sum += source[start + Math.min(i + radius + 1, length - 1) * step] - source[start + Math.max(i - radius, 0) * step];
      }
    }
    source = target;
  }
  return source;
}

// ---- 3. The surface ----

const CORNERS = [[0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 1, 0], [0, 0, 1], [1, 0, 1], [0, 1, 1], [1, 1, 1]];
const EDGES = [[0, 1], [2, 3], [4, 5], [6, 7], [0, 2], [1, 3], [4, 6], [5, 7], [0, 4], [1, 5], [2, 6], [3, 7]];

/**
 * The surface where the grid crosses [iso]: one vertex in every cell the surface passes through (at the average of the places
 * it crosses the cell's edges) and a quad, cut into two triangles, across every grid edge it crosses. Triangles face outward
 * (the side where the value is lower).
 */
export function surfaceNets({ field, dims, origin, voxel }, iso = 0.5) {
  const [nx, ny, nz] = dims;
  const cellAt = new Int32Array(nx * ny * nz).fill(-1);
  const positions = [];
  const value = (x, y, z) => field[(z * ny + y) * nx + x];
  const corner = new Float32Array(8);

  for (let z = 0; z < nz - 1; z++) for (let y = 0; y < ny - 1; y++) for (let x = 0; x < nx - 1; x++) {
    let inside = 0;
    for (let c = 0; c < 8; c++) {
      const [dx, dy, dz] = CORNERS[c];
      corner[c] = value(x + dx, y + dy, z + dz);
      if (corner[c] >= iso) inside++;
    }
    if (inside === 0 || inside === 8) continue;
    let sx = 0, sy = 0, sz = 0, crossings = 0;
    for (const [a, b] of EDGES) {
      if ((corner[a] >= iso) === (corner[b] >= iso)) continue;
      const t = (iso - corner[a]) / (corner[b] - corner[a]);
      sx += CORNERS[a][0] + t * (CORNERS[b][0] - CORNERS[a][0]);
      sy += CORNERS[a][1] + t * (CORNERS[b][1] - CORNERS[a][1]);
      sz += CORNERS[a][2] + t * (CORNERS[b][2] - CORNERS[a][2]);
      crossings++;
    }
    cellAt[(z * ny + y) * nx + x] = positions.length / 3;
    positions.push(origin[0] + (x + sx / crossings) * voxel, origin[1] + (y + sy / crossings) * voxel, origin[2] + (z + sz / crossings) * voxel);
  }

  const indices = [];
  const cell = (x, y, z) => cellAt[(z * ny + y) * nx + x];
  for (let z = 1; z < nz - 1; z++) for (let y = 1; y < ny - 1; y++) for (let x = 1; x < nx - 1; x++) {
    const here = value(x, y, z) >= iso;
    for (let axis = 0; axis < 3; axis++) {
      const nxt = [x + (axis === 0 ? 1 : 0), y + (axis === 1 ? 1 : 0), z + (axis === 2 ? 1 : 0)];
      if (nxt[0] >= nx || nxt[1] >= ny || nxt[2] >= nz) continue;
      if (here === (value(...nxt) >= iso)) continue;
      // The four cells that share this edge, in a ring around it.
      const u = (axis + 1) % 3, v = (axis + 2) % 3;
      const ring = [[0, 0], [1, 0], [1, 1], [0, 1]].map(([du, dv]) => {
        const at = [x, y, z];
        at[u] -= 1 - du; at[v] -= 1 - dv;
        return cell(at[0], at[1], at[2]);
      });
      if (ring.some((id) => id < 0)) continue;
      const [a, b, c, d] = here ? ring : [ring[0], ring[3], ring[2], ring[1]];
      indices.push(a, b, c, a, c, d);
    }
  }
  return { positions: Float32Array.from(positions), indices: Uint32Array.from(indices) };
}
