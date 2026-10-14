// Reading the atlas and the mesh operations the build uses: weld, simplify, bounds. Plain functions on typed arrays.
import fs from 'node:fs';
import path from 'node:path';
import { MeshoptSimplifier } from 'meshoptimizer';

await MeshoptSimplifier.ready;

// ---- Reading the atlas ----

export function readAtlas(dir) {
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'public/models/atlas.json'), 'utf8'));
  const chunks = manifest.chunks.map((c) => fs.readFileSync(path.join(dir, 'public', c.url)));
  return manifest.parts.map((p) => {
    const b = chunks[p.chunk];
    const at = (offset, bytes) => b.buffer.slice(b.byteOffset + offset, b.byteOffset + offset + bytes);
    return {
      name: p.name,
      system: p.system,
      positions: new Float32Array(at(p.positions, p.vertexCount * 12)),
      indices: new Uint32Array(at(p.indices, p.indexCount * 4)),
    };
  });
}

export const centreX = (part) => {
  let lo = Infinity, hi = -Infinity;
  for (let i = 0; i < part.positions.length; i += 3) { lo = Math.min(lo, part.positions[i]); hi = Math.max(hi, part.positions[i]); }
  return (lo + hi) / 2;
};


// ---- Geometry ----

/** Joins parts into one mesh and merges vertices that sit in the same place (0.01 mm), dropping flat triangles. */
export function mergeAndWeld(parts) {
  const lookup = new Map();
  const positions = [];
  const indices = [];
  const quantum = 1e-5;
  for (const part of parts) {
    const remap = new Uint32Array(part.positions.length / 3);
    for (let v = 0; v < remap.length; v++) {
      const x = part.positions[v * 3], y = part.positions[v * 3 + 1], z = part.positions[v * 3 + 2];
      const key = `${Math.round(x / quantum)},${Math.round(y / quantum)},${Math.round(z / quantum)}`;
      let id = lookup.get(key);
      if (id === undefined) { id = positions.length / 3; lookup.set(key, id); positions.push(x, y, z); }
      remap[v] = id;
    }
    for (let i = 0; i < part.indices.length; i += 3) {
      const a = remap[part.indices[i]], b = remap[part.indices[i + 1]], c = remap[part.indices[i + 2]];
      if (a !== b && b !== c && a !== c) indices.push(a, b, c);
    }
  }
  return { positions: new Float32Array(positions), indices: new Uint32Array(indices) };
}

/** Fewer triangles, keeping the shape to within [error] of the mesh's size; then only the vertices still used, with smooth normals. */
export function simplify({ positions, indices }, targetTriangles, error) {
  let result = indices;
  if (indices.length / 3 > targetTriangles) {
    [result] = MeshoptSimplifier.simplify(indices, positions, 3, targetTriangles * 3, error, []);
  }
  const used = new Map();
  const outPositions = [];
  const outIndices = new Uint32Array(result.length);
  result.forEach((v, i) => {
    let id = used.get(v);
    if (id === undefined) { id = used.size; used.set(v, id); outPositions.push(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]); }
    outIndices[i] = id;
  });
  const pos = new Float32Array(outPositions);
  const normals = new Float32Array(pos.length);
  const firstFace = new Float32Array(pos.length); // each vertex's first face normal, for a vertex whose faces cancel each other out
  for (let t = 0; t < outIndices.length; t += 3) {
    const [a, b, c] = [outIndices[t] * 3, outIndices[t + 1] * 3, outIndices[t + 2] * 3];
    const ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
    const vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
    const nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx; // the length is the area: bigger faces count more
    const length = Math.hypot(nx, ny, nz);
    for (const v of [a, b, c]) {
      normals[v] += nx; normals[v + 1] += ny; normals[v + 2] += nz;
      if (!firstFace[v] && !firstFace[v + 1] && !firstFace[v + 2] && length > 0) { firstFace[v] = nx / length; firstFace[v + 1] = ny / length; firstFace[v + 2] = nz / length; }
    }
  }
  for (let v = 0; v < normals.length; v += 3) {
    // Two layers of a thin sheet facing opposite ways can add up to nothing: then the face's own direction is used.
    const sum = Math.hypot(normals[v], normals[v + 1], normals[v + 2]);
    if (sum < 1e-9) {
      const own = Math.hypot(firstFace[v], firstFace[v + 1], firstFace[v + 2]);
      if (own > 0) { normals[v] = firstFace[v]; normals[v + 1] = firstFace[v + 1]; normals[v + 2] = firstFace[v + 2]; } else { normals[v] = 0; normals[v + 1] = 1; normals[v + 2] = 0; }
      continue;
    }
    normals[v] /= sum; normals[v + 1] /= sum; normals[v + 2] /= sum;
  }
  return { positions: pos, normals, indices: outIndices };
}

export const bounds = (positions) => {
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < positions.length; i += 3) for (let a = 0; a < 3; a++) {
    if (positions[i + a] < min[a]) min[a] = positions[i + a];
    if (positions[i + a] > max[a]) max[a] = positions[i + a];
  }
  return { min, max };
};
export const round = (list) => list.map((v) => Math.round(v * 10_000) / 10_000);
export const joinBounds = (list) => ({ min: [0, 1, 2].map((a) => Math.min(...list.map((b) => b.min[a]))), max: [0, 1, 2].map((a) => Math.max(...list.map((b) => b.max[a]))) });

