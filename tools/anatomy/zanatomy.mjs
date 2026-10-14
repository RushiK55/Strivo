// Reads the Z-Anatomy-derived muscle model (lucgrn3/anatomy-models, muscles.glb + index.json): a glTF that uses meshopt
// compression and quantised attributes, with one node per structure named "ta2-<Terminologia Anatomica 2 id>.<l|r|m>".
// Only what the build needs is decoded: positions and triangle indices, one mesh per node.
import fs from 'node:fs';
import path from 'node:path';
import { MeshoptDecoder } from 'meshoptimizer/decoder';

await MeshoptDecoder.ready;

const COMPONENTS = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4 };
const ARRAYS = { 5120: Int8Array, 5121: Uint8Array, 5122: Int16Array, 5123: Uint16Array, 5125: Uint32Array, 5126: Float32Array };

/** The decoded bytes of one buffer view (decompressing it when it uses EXT_meshopt_compression). */
function viewBytes(gltf, bin, index, cache) {
  if (cache.has(index)) return cache.get(index);
  const view = gltf.bufferViews[index];
  const packed = view.extensions?.EXT_meshopt_compression;
  let bytes;
  if (packed) {
    bytes = new Uint8Array(packed.count * packed.byteStride);
    MeshoptDecoder.decodeGltfBuffer(bytes, packed.count, packed.byteStride, bin.subarray(packed.byteOffset ?? 0, (packed.byteOffset ?? 0) + packed.byteLength), packed.mode, packed.filter);
  } else {
    bytes = bin.subarray(view.byteOffset ?? 0, (view.byteOffset ?? 0) + view.byteLength);
  }
  cache.set(index, bytes);
  return bytes;
}

function readAccessor(gltf, bin, index, cache) {
  const accessor = gltf.accessors[index];
  const view = gltf.bufferViews[accessor.bufferView];
  const bytes = viewBytes(gltf, bin, accessor.bufferView, cache);
  const Type = ARRAYS[accessor.componentType];
  const components = COMPONENTS[accessor.type];
  const stride = view.byteStride ?? components * Type.BYTES_PER_ELEMENT;
  const start = accessor.byteOffset ?? 0;
  if (stride === components * Type.BYTES_PER_ELEMENT) {
    const copy = bytes.slice(start, start + accessor.count * stride);
    return new Type(copy.buffer, copy.byteOffset, accessor.count * components);
  }
  const out = new Type(accessor.count * components);
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const read = { 5126: (o) => dv.getFloat32(o, true), 5125: (o) => dv.getUint32(o, true), 5123: (o) => dv.getUint16(o, true), 5121: (o) => dv.getUint8(o), 5122: (o) => dv.getInt16(o, true), 5120: (o) => dv.getInt8(o) }[accessor.componentType];
  for (let i = 0; i < accessor.count; i++) for (let c = 0; c < components; c++) out[i * components + c] = read(start + i * stride + c * Type.BYTES_PER_ELEMENT);
  return out;
}

/** Applies a node's scale, rotation and translation (the file is quantised, so a node may carry a transform). */
function nodeMatrixApply(node, positions) {
  if (!node.matrix && !node.scale && !node.translation && !node.rotation) return positions;
  if (node.rotation) throw new Error(`node ${node.name} has a rotation, which this reader does not handle`);
  const s = node.scale ?? [1, 1, 1], t = node.translation ?? [0, 0, 0];
  const out = new Float32Array(positions.length);
  for (let i = 0; i < positions.length; i += 3) for (let a = 0; a < 3; a++) out[i + a] = positions[i + a] * s[a] + t[a];
  return out;
}

/** Every structure in the glb: [{ name, positions (Float32Array, metres), indices (Uint32Array) }]. */
export function readStructures(glbPath) {
  const file = fs.readFileSync(glbPath);
  const jsonLength = file.readUInt32LE(12);
  const gltf = JSON.parse(file.subarray(20, 20 + jsonLength).toString('utf8'));
  const bin = new Uint8Array(file.buffer, file.byteOffset + 20 + jsonLength + 8, file.readUInt32LE(20 + jsonLength));
  const cache = new Map();
  return gltf.nodes.filter((n) => n.mesh !== undefined).map((node) => {
    const primitive = gltf.meshes[node.mesh].primitives[0];
    const positions = nodeMatrixApply(node, readAccessor(gltf, bin, primitive.attributes.POSITION, cache));
    if (gltf.accessors[primitive.attributes.POSITION].normalized) throw new Error('quantised positions are not handled');
    return { name: node.name, positions, indices: Uint32Array.from(readAccessor(gltf, bin, primitive.indices, cache)) };
  });
}

/** index.json next to the glb: per node its layer, depth, centre, box and triangle count. */
export function readIndex(dir) {
  return JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8'));
}
