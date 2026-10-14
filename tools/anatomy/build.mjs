// Builds the body models the app and the dashboard show, from the Human-Atlas (male) and female-atlas data plus six muscles from
// Z-Anatomy:
//   models/anatomy/male.glb, female.glb   one node per muscle group and side ("biceps.L"), a "Skeleton" mesh, and a "Skin" mesh: a
//                                         smooth skin-like layer over everything, which the viewers fade out to show the muscles
//   models/anatomy/muscles.json           the catalogue: every group, where it is, what it is called, which exercises belong to it
//
// node build.mjs <maleAtlasDir> <femaleAtlasDir> <zanatomyDir> [outDir]
//   (or set ANATOMY_MALE_ATLAS, ANATOMY_FEMALE_ATLAS and ANATOMY_ZANATOMY; zanatomyDir holds muscles.glb from lucgrn3/anatomy-models)
//
// What it does to the source meshes: keeps only what a gym app needs (see groups.mjs), joins the 400 loose muscles into about
// 30 groups (adding the muscles BodyParts3D lacks from Z-Anatomy), welds duplicate vertices, simplifies each group to a share of its
// triangles, recomputes smooth normals, and puts both bodies on the same ground and centre line. The skin is built around the
// muscles and bones (shell.mjs). The female skin also takes two smooth bumps on the chest as a soft bust (placed by the atlas's nipples);
// no breast, areola or nipple is ever written to the file.
// Plain glTF 2.0: no compression extension, so every viewer can read it.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { bounds, centreX, joinBounds, mergeAndWeld, readAtlas, round, simplify } from './geometry.mjs';
import { ABOUT, DROPPED_BONES, GROUPS, MALE_SPINE_IN_FEMALE, MISFILED_MUSCLES, ZANATOMY_MUSCLES, classify, isNipple, sideOf, zanatomyName } from './groups.mjs';
import { bump, fillBody, frontSurface, sampleMeshes, surfaceNets } from './shell.mjs';
import { DisplacementField, matchMuscles } from './warp.mjs';
import { readStructures } from './zanatomy.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const maleDir = path.resolve(process.argv[2] ?? process.env.ANATOMY_MALE_ATLAS ?? '');
const femaleDir = path.resolve(process.argv[3] ?? process.env.ANATOMY_FEMALE_ATLAS ?? '');
const zanatomyDir = path.resolve(process.argv[4] ?? process.env.ANATOMY_ZANATOMY ?? '');
const outDir = path.resolve(process.argv[5] ?? path.join(here, '..', '..', 'models', 'anatomy'));
for (const [label, file] of [['male atlas', path.join(maleDir, 'public/models/atlas.json')], ['female atlas', path.join(femaleDir, 'public/models/atlas.json')], ['Z-Anatomy muscles', path.join(zanatomyDir, 'muscles.glb')]]) {
  if (!fs.existsSync(file)) {
    console.error(`Cannot find the ${label} (${file}). Usage: node build.mjs <Human-Atlas> <female-atlas> <folder with muscles.glb> [outDir]`);
    process.exit(1);
  }
}

/** How many triangles the muscles of one body may have in all (the groups' own detail is scaled down to fit). */
const MUSCLE_BUDGET = 150_000;
const SKELETON_TRIANGLES = 30_000;
const MIN_GROUP_TRIANGLES = 150;
/** The skin: how fine its grid is, how far (in cells) it stands off the muscles, how much it is rounded off, and its size. */
const SHELL = { voxel: 0.005, grow: 2, radius: 2, passes: 2 };
const SKIN_TRIANGLES = 40_000;
const SAMPLE_SPACING = 0.004;
/**
 * The female skin's bust: a smooth bump on each side of the chest, centred on the atlas's nipple. `wide` and `high` are its
 * half-width and half-height on the chest wall and `out` how far it stands out from the wall in the middle, easing to nothing at
 * the edge (the skin's own growth of about 1 cm and the smoothing round it further). It follows the real front of the body under
 * it. Small on purpose: it is a hint of a shape.
 */
const BUST = { wide: 0.066, high: 0.06, out: 0.026 };
/** The head, from the chin up: closed over (no eye sockets) so the skin there is a smooth head. Measured from the top of the head. */
const HEAD = { height: 0.215, closing: 6 };
const COLORS = { muscle: '#a85b50', skeleton: '#e2d9ba', skin: '#9aa5bd' };

/** Sorts a body's atlas parts into muscles (by group and side), bones and (female only) the nipples, whose place the bust is centred on, and reports what it dropped. */
function select(parts, { female }) {
  const muscles = new Map(); // "group.side" -> parts
  const bones = [], nipples = [], dropped = new Map(), unknown = [];
  for (const part of parts) {
    const isMuscle = part.system === 'muscular'
      || (part.system === 'skeletal' && MISFILED_MUSCLES.test(part.name))
      || (part.system === 'connective' && /tensor fasciae latae/i.test(part.name));
    if (isMuscle) {
      const place = classify(part.name);
      if (place.unknown) { unknown.push(part.name); continue; }
      if (place.dropped) { dropped.set(place.dropped, (dropped.get(place.dropped) ?? 0) + 1); continue; }
      const key = `${place.group}.${sideOf(part.name, centreX(part))}`;
      muscles.set(key, [...(muscles.get(key) ?? []), part]);
    } else if (part.system === 'skeletal') {
      const why = DROPPED_BONES.test(part.name) ? 'teeth, larynx cartilage, bone interiors, knee ligaments'
        : female && MALE_SPINE_IN_FEMALE.test(part.name) ? 'the male spine inside the female atlas' : null;
      if (why) dropped.set(why, (dropped.get(why) ?? 0) + 1);
      else bones.push(part);
    } else if (female && part.system === 'reproductive' && isNipple(part.name)) {
      nipples.push(part);
    }
  }
  if (unknown.length) {
    console.error(`These muscles are in no group and not on the drop list (add them to groups.mjs):\n  ${[...new Set(unknown)].join('\n  ')}`);
    process.exit(1);
  }
  return { muscles, bones, nipples, dropped };
}

// ---- glTF ----

const linear = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));

class GlbWriter {
  constructor(name, body) {
    this.gltf = {
      asset: { version: '2.0', generator: 'Strivo tools/anatomy/build.mjs' },
      scene: 0,
      scenes: [{ name, nodes: [0] }],
      nodes: [{ name, children: [], extras: { body, units: 'm', up: 'Y', front: '+Z' } }],
      meshes: [], materials: [], accessors: [], bufferViews: [], buffers: [],
    };
    this.blobs = [];
    this.offset = 0;
    this.materials = new Map();
  }

  material(name, color) {
    if (!this.materials.has(name)) {
      this.materials.set(name, this.gltf.materials.length);
      this.gltf.materials.push({ name, pbrMetallicRoughness: { baseColorFactor: [...linear(color), 1], metallicFactor: 0, roughnessFactor: 0.6 }, doubleSided: true });
    }
    return this.materials.get(name);
  }

  group(name, parent = 0) {
    this.gltf.nodes.push({ name, children: [] });
    const id = this.gltf.nodes.length - 1;
    this.gltf.nodes[parent].children.push(id);
    return id;
  }

  view(typed, target) {
    const buffer = Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength);
    const padding = Buffer.alloc((4 - (buffer.length % 4)) % 4);
    this.blobs.push(buffer, padding);
    this.gltf.bufferViews.push({ buffer: 0, byteOffset: this.offset, byteLength: buffer.length, target });
    this.offset += buffer.length + padding.length;
    return this.gltf.bufferViews.length - 1;
  }

  accessor(view, componentType, count, type, extra = {}) {
    this.gltf.accessors.push({ bufferView: view, componentType, count, type, ...extra });
    return this.gltf.accessors.length - 1;
  }

  mesh(name, geometry, material, parent, extras) {
    const { positions, normals, indices } = geometry;
    const big = positions.length / 3 > 65_535;
    const { min, max } = bounds(positions);
    const aPos = this.accessor(this.view(positions, 34962), 5126, positions.length / 3, 'VEC3', { min, max });
    const aNrm = this.accessor(this.view(normals, 34962), 5126, normals.length / 3, 'VEC3');
    const aIdx = this.accessor(this.view(big ? indices : Uint16Array.from(indices), 34963), big ? 5125 : 5123, indices.length, 'SCALAR');
    this.gltf.meshes.push({ name, primitives: [{ attributes: { POSITION: aPos, NORMAL: aNrm }, indices: aIdx, material, mode: 4 }] });
    this.gltf.nodes.push({ name, mesh: this.gltf.meshes.length - 1, ...(extras ? { extras } : {}) });
    this.gltf.nodes[parent].children.push(this.gltf.nodes.length - 1);
  }

  toBuffer() {
    const bin = Buffer.concat(this.blobs);
    this.gltf.buffers.push({ byteLength: bin.length });
    let json = Buffer.from(JSON.stringify(this.gltf), 'utf8');
    json = Buffer.concat([json, Buffer.alloc((4 - (json.length % 4)) % 4, 0x20)]);
    const total = 12 + 8 + json.length + 8 + bin.length;
    const head = Buffer.alloc(12);
    head.writeUInt32LE(0x46546c67, 0); head.writeUInt32LE(2, 4); head.writeUInt32LE(total, 8);
    const jsonHead = Buffer.alloc(8); jsonHead.writeUInt32LE(json.length, 0); jsonHead.writeUInt32LE(0x4e4f534a, 4);
    const binHead = Buffer.alloc(8); binHead.writeUInt32LE(bin.length, 0); binHead.writeUInt32LE(0x004e4942, 4);
    return Buffer.concat([head, jsonHead, json, binHead, bin]);
  }
}

// ---- One body ----

/** The muscles BodyParts3D lacks, from Z-Anatomy, as atlas-style parts (male-shaped, in the BodyParts3D frame). */
function zanatomyMuscles() {
  return readStructures(path.join(zanatomyDir, 'muscles.glb'))
    .map((s) => ({ name: zanatomyName(s.name), system: 'muscular', positions: s.positions, indices: s.indices }))
    .filter((part) => part.name);
}

/**
 * One side of the bust: a smooth bump centred on [nipple] (an atlas part) sideways and up and down, lying on the front of the
 * body there ([bodyPoints] are points over every muscle and bone) and rising BUST.out above it. Placed on the body as it is in
 * this build, not on where the atlas's breast reached.
 */
function bustDome(nipple, bodyPoints) {
  const { min, max } = bounds(nipple.positions);
  const x = (min[0] + max[0]) / 2, y = (min[1] + max[1]) / 2;
  const surface = frontSurface(bodyPoints, [x - BUST.wide, x + BUST.wide, y - BUST.high, y + BUST.high]);
  return bump([x, y], BUST, surface);
}

/** Two lists of points (x y z) as one. */
const joinPoints = (a, b) => { const all = new Float32Array(a.length + b.length); all.set(a); all.set(b, a.length); return all; };

function buildBody(body, atlasDir) {
  const parts = readAtlas(atlasDir);
  const added = zanatomyMuscles();
  if (added.length !== Object.keys(ZANATOMY_MUSCLES).length * 2) throw new Error(`expected ${Object.keys(ZANATOMY_MUSCLES).length * 2} Z-Anatomy parts, found ${added.length}`);
  if (body === 'female') {
    // The female atlas's muscles are the male ones reshaped: move the new male-shaped muscles the way their neighbours move.
    const field = new DisplacementField(matchMuscles(readAtlas(maleDir).filter((p) => p.system === 'muscular'), parts.filter((p) => p.system === 'muscular'), centreX));
    for (const part of added) part.positions = field.apply(part.positions);
  }
  const { muscles, bones, nipples, dropped } = select([...parts, ...added], { female: body === 'female' });
  const everyPart = [...[...muscles.values()].flat(), ...bones];

  // The same ground and centre line for both bodies: feet on y = 0, centred left to right and front to back.
  const all = joinBounds(everyPart.map((p) => bounds(p.positions)));
  const shift = [-(all.min[0] + all.max[0]) / 2, -all.min[1], -(all.min[2] + all.max[2]) / 2];
  for (const part of [...everyPart, ...nipples]) for (let i = 0; i < part.positions.length; i += 3) for (let a = 0; a < 3; a++) part.positions[i + a] += shift[a];

  const sourceTriangles = (list) => list.reduce((sum, p) => sum + p.indices.length / 3, 0);
  const detail = Object.fromEntries(GROUPS.map((g) => [g.id, g.detail]));

  // How many triangles each muscle mesh gets: its group's share of the source, scaled down together if over the budget.
  const wanted = new Map([...muscles].map(([key, list]) => [key, Math.max(MIN_GROUP_TRIANGLES, sourceTriangles(list) * detail[key.split('.')[0]])]));
  const total = [...wanted.values()].reduce((a, b) => a + b, 0);
  const scale = Math.min(1, MUSCLE_BUDGET / total);

  const writer = new GlbWriter(body === 'male' ? 'Male body' : 'Female body', body);
  const muscleNode = writer.group('Muscles');
  const catalogue = new Map(); // group id -> info
  const geometries = new Map(); // group id -> its simplified meshes
  let muscleTriangles = 0;
  for (const g of GROUPS) {
    for (const side of ['L', 'R', 'C']) {
      const key = `${g.id}.${side}`;
      const list = muscles.get(key);
      if (!list) continue;
      const source = sourceTriangles(list);
      const geometry = simplify(mergeAndWeld(list), Math.max(MIN_GROUP_TRIANGLES, Math.round(wanted.get(key) * scale)), 0.02);
      writer.mesh(key, geometry, writer.material(`muscle-${g.id}`, COLORS.muscle), muscleNode, { group: g.id, side, layer: g.layer });
      const triangles = geometry.indices.length / 3;
      muscleTriangles += triangles;
      const entry = catalogue.get(g.id) ?? { sources: 0, triangles: 0, nodes: [], boxes: {} };
      entry.sources += source; entry.triangles += triangles; entry.nodes.push(key);
      entry.boxes[side] = bounds(geometry.positions);
      catalogue.set(g.id, entry);
      geometries.set(g.id, [...(geometries.get(g.id) ?? []), geometry]);
    }
  }

  const skeletonGeometry = simplify(mergeAndWeld(bones), SKELETON_TRIANGLES, 0.01);
  writer.mesh('Skeleton', skeletonGeometry, writer.material('skeleton', COLORS.skeleton), writer.group('Skeleton'), { group: 'skeleton' });

  // The skin: a smooth layer around all of it (the head closed over first, so there are no eye sockets).
  const height = all.max[1] - all.min[1];
  // The female skin also takes a soft bust: two smooth bumps on the chest wall, only there to shape the skin (they are not written to the file).
  const bodyPoints = sampleMeshes([...[...geometries.values()].flat(), skeletonGeometry], SAMPLE_SPACING);
  const bustPoints = sampleMeshes(nipples.map((nipple) => bustDome(nipple, bodyPoints)), SAMPLE_SPACING);
  const shell = surfaceNets(fillBody(
    joinPoints(bodyPoints, bustPoints),
    { ...SHELL, closing: { minY: height - HEAD.height, radius: HEAD.closing } },
  ));
  const skinGeometry = simplify(mergeAndWeld([shell]), SKIN_TRIANGLES, 0.004);
  writer.mesh('Skin', skinGeometry, writer.material('skin', COLORS.skin), writer.group('Skin'), { group: 'skin' });

  const buffer = writer.toBuffer();
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, `${body}.glb`), buffer);

  const final = joinBounds([...catalogue.values()].flatMap((e) => Object.values(e.boxes)).concat([bounds(skeletonGeometry.positions), bounds(skinGeometry.positions)]));
  console.log(`${body}.glb  ${(buffer.length / 1048576).toFixed(2)} MB  muscles ${muscleTriangles.toLocaleString()} + skeleton ${(skeletonGeometry.indices.length / 3).toLocaleString()} + skin ${(skinGeometry.indices.length / 3).toLocaleString()} triangles; ${writer.gltf.meshes.length} meshes; ` +
    `source ${(everyPart.reduce((s, p) => s + p.indices.length / 3, 0)).toLocaleString()} triangles; dropped: ${[...dropped].map(([why, n]) => `${n} ${why}`).join(', ')}`);
  return { catalogue, bytes: buffer.length, height: final.max[1], box: final, triangles: muscleTriangles + (skeletonGeometry.indices.length + skinGeometry.indices.length) / 3 };
}

const male = buildBody('male', maleDir);
const female = buildBody('female', femaleDir);

// ---- The catalogue ----

const boxOf = (b) => ({ min: round(b.min), max: round(b.max) });
const bodyInfo = (entry) => entry && ({
  nodes: entry.nodes,
  box: boxOf(joinBounds(Object.values(entry.boxes))),
  sides: Object.fromEntries(Object.entries(entry.boxes).map(([side, b]) => [side, boxOf(b)])),
  triangles: entry.triangles,
  sourceTriangles: entry.sources,
});

/** The groups made of the muscles taken from Z-Anatomy. */
const zanatomyGroups = new Set(Object.values(ZANATOMY_MUSCLES).map((name) => classify(name).group));

const catalogue = {
  version: 3,
  note: 'Generated by tools/anatomy/build.mjs. Units are metres, Y is up, the body faces +Z, +X is the body\'s left. Each group has one node per side in the .glb ("biceps.L", "biceps.R"; "C" is the middle). The .glb also holds a "Skeleton" mesh and a "Skin" mesh, a smooth layer over everything.',
  bodies: {
    male: { file: 'male.glb', bytes: male.bytes, height: round([male.height])[0], box: boxOf(male.box), triangles: male.triangles },
    female: { file: 'female.glb', bytes: female.bytes, height: round([female.height])[0], box: boxOf(female.box), triangles: female.triangles },
  },
  groups: GROUPS.map((g) => ({
    id: g.id,
    label: g.label,
    about: ABOUT[g.id],
    libraryGroup: g.libraryGroup,
    view: g.view,
    layer: g.layer,
    source: zanatomyGroups.has(g.id) ? 'z-anatomy' : 'bodyparts3d',
    male: bodyInfo(male.catalogue.get(g.id)),
    female: bodyInfo(female.catalogue.get(g.id)),
  })),
  notIncluded: ['the muscles of the face and scalp (masseter, temporalis, frontalis ...)'],
};
fs.writeFileSync(path.join(outDir, 'muscles.json'), `${JSON.stringify(catalogue, null, 1)}\n`);
console.log(`muscles.json  ${catalogue.groups.length} groups`);
