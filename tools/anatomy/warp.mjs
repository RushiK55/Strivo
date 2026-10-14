// Fits a mesh made for the male body onto the female body.
//
// The female atlas's muscles are the male ones reshaped: a part has the same vertices and triangles in both, only moved. So
// at every vertex of every muscle we know how far the reshaping moved that point of the body. A new male-shaped mesh (the
// latissimus dorsi from Z-Anatomy, say) is moved by the average movement of the muscle vertices around it, nearest ones
// counting most (inverse-distance weighting). Nothing is invented: the new muscle follows the neighbours it lies against.

const CELL = 0.02; // metres: the size of the cells the vertices are sorted into

/**
 * Pairs each male muscle with its female one: the same name, and left with left by their position. Returns the pairs whose
 * vertices correspond (the same count), as { name, male, female } position arrays.
 */
export function matchMuscles(maleParts, femaleParts, centreX) {
  const byName = (parts) => {
    const map = new Map();
    for (const part of parts) { const key = part.name.toLowerCase().trim(); map.set(key, [...(map.get(key) ?? []), part]); }
    return map;
  };
  const female = byName(femaleParts);
  const pairs = [];
  for (const [key, males] of byName(maleParts)) {
    const others = female.get(key);
    if (!others) continue;
    const a = [...males].sort((p, q) => centreX(p) - centreX(q)), b = [...others].sort((p, q) => centreX(p) - centreX(q));
    a.forEach((m, i) => { if (b[i] && b[i].positions.length === m.positions.length) pairs.push({ name: m.name, male: m.positions, female: b[i].positions }); });
  }
  return pairs;
}

/** A displacement field from pairs of matching [maleVertices, femaleVertices] (Float32Array, x y z each, same order). */
export class DisplacementField {
  constructor(pairs) {
    let count = 0;
    for (const { male } of pairs) count += male.length / 3;
    this.points = new Float32Array(count * 3);
    this.moves = new Float32Array(count * 3);
    this.owner = new Int32Array(count); // which pair a point came from (to leave one out when testing)
    this.cells = new Map();
    let n = 0;
    pairs.forEach(({ male, female }, owner) => {
      for (let v = 0; v < male.length; v += 3) {
        for (let a = 0; a < 3; a++) { this.points[n * 3 + a] = male[v + a]; this.moves[n * 3 + a] = female[v + a] - male[v + a]; }
        this.owner[n] = owner;
        const key = this.#key(male[v], male[v + 1], male[v + 2]);
        const list = this.cells.get(key);
        if (list) list.push(n); else this.cells.set(key, [n]);
        n++;
      }
    });
  }

  #key(x, y, z) {
    return (Math.floor(x / CELL) + 1024) + 4096 * (Math.floor(y / CELL) + 1024) + 16_777_216 * (Math.floor(z / CELL) + 1024);
  }

  /** How far the reshaping moves the point (x, y, z): the weighted mean over the nearest [k] vertices. Skips [skipOwner]'s own. */
  at(x, y, z, { k = 16, skipOwner = -1 } = {}) {
    const cx = Math.floor(x / CELL), cy = Math.floor(y / CELL), cz = Math.floor(z / CELL);
    let best = []; // [distance squared, point] of the nearest so far, sorted
    for (let r = 0; r <= 12; r++) {
      for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) for (let dz = -r; dz <= r; dz++) {
        if (Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz)) !== r) continue; // only the shell of this radius
        const list = this.cells.get((cx + dx + 1024) + 4096 * (cy + dy + 1024) + 16_777_216 * (cz + dz + 1024));
        if (!list) continue;
        for (const p of list) {
          if (this.owner[p] === skipOwner) continue;
          const d = (this.points[p * 3] - x) ** 2 + (this.points[p * 3 + 1] - y) ** 2 + (this.points[p * 3 + 2] - z) ** 2;
          if (best.length < k || d < best[best.length - 1][0]) {
            best.push([d, p]);
            best.sort((a, b) => a[0] - b[0]);
            if (best.length > k) best.pop();
          }
        }
      }
      // every vertex within r cells has been seen, so the k nearest are known once the k-th is that close
      if (best.length === k && Math.sqrt(best[k - 1][0]) <= r * CELL) break;
    }
    if (!best.length) {
      // Nothing within reach of the cells: look at every point (rare; a mesh that far from the body is not really being fitted).
      for (let p = 0; p < this.owner.length; p++) {
        if (this.owner[p] === skipOwner) continue;
        const d = (this.points[p * 3] - x) ** 2 + (this.points[p * 3 + 1] - y) ** 2 + (this.points[p * 3 + 2] - z) ** 2;
        if (best.length < k || d < best[best.length - 1][0]) {
          best.push([d, p]);
          best.sort((a, b) => a[0] - b[0]);
          if (best.length > k) best.pop();
        }
      }
      if (!best.length) return [0, 0, 0];
    }
    let sw = 0, mx = 0, my = 0, mz = 0;
    for (const [d, p] of best) {
      const w = 1 / (d + 4e-6); // (2 mm)^2 keeps a vertex that sits on the point from taking everything
      sw += w; mx += w * this.moves[p * 3]; my += w * this.moves[p * 3 + 1]; mz += w * this.moves[p * 3 + 2];
    }
    return [mx / sw, my / sw, mz / sw];
  }

  /** A copy of [positions] moved onto the female body. */
  apply(positions, options) {
    const out = new Float32Array(positions.length);
    for (let v = 0; v < positions.length; v += 3) {
      const [mx, my, mz] = this.at(positions[v], positions[v + 1], positions[v + 2], options);
      out[v] = positions[v] + mx; out[v + 1] = positions[v + 1] + my; out[v + 2] = positions[v + 2] + mz;
    }
    return out;
  }
}
