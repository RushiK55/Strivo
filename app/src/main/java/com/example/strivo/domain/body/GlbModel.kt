package com.example.strivo.domain.body

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What a piece of the body model is. */
enum class PartKind { Muscle, Skeleton, Skin }

/** One mesh of the body model: its triangles, and what part of the body it is. */
class GlbPart(
    val name: String,
    val kind: PartKind,
    /** The muscle's id ("lats") for a muscle; "skeleton" or "skin" for the others. */
    val group: String,
    /** "L" or "R" for a muscle on one side, "C" for the middle (and for the skeleton and the skin). */
    val side: String,
    val layer: MuscleLayer,
    /** x y z for each vertex. */
    val positions: FloatArray,
    /** The vertex normals, the same length as [positions]. */
    val normals: FloatArray,
    /** Three vertex numbers for each triangle. */
    val indices: IntArray,
) {
    val triangleCount: Int get() = indices.size / 3
    val vertexCount: Int get() = positions.size / 3
}

class GlbModel(val parts: List<GlbPart>) {
    val triangleCount: Int get() = parts.sumOf { it.triangleCount }
}

/**
 * Reads the body models (assets/male.glb and female.glb, written by tools/anatomy): plain glTF 2.0 in one binary file, each piece a
 * node with a mesh of float positions and normals and 16- or 32-bit indices, and the piece's muscle, side and layer in the node's
 * `extras`. It reads only what those files hold (no animation, materials, textures or compression) and gives up (null) on anything
 * else, so a broken file shows nothing rather than a broken body.
 */
object GlbReader {
    private const val MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942
    private const val FLOAT = 5126
    private const val UNSIGNED_SHORT = 5123
    private const val UNSIGNED_INT = 5125

    fun read(bytes: ByteArray): GlbModel? = runCatching { parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)) }.getOrNull()

    private fun parse(buffer: ByteBuffer): GlbModel? {
        if (buffer.remaining() < 20 || buffer.getInt(0) != MAGIC || buffer.getInt(4) != 2) return null
        var offset = 12
        var json: JSONObject? = null
        var binStart = -1
        var binLength = 0
        while (offset + 8 <= buffer.limit()) {
            val length = buffer.getInt(offset)
            val type = buffer.getInt(offset + 4)
            val start = offset + 8
            if (length < 0 || start + length > buffer.limit()) return null
            when (type) {
                JSON_CHUNK -> json = JSONObject(String(buffer.array(), buffer.arrayOffset() + start, length, Charsets.UTF_8))
                BIN_CHUNK -> { binStart = start; binLength = length }
            }
            offset = start + length
        }
        val gltf = json ?: return null
        if (binStart < 0) return null

        val accessors = gltf.getJSONArray("accessors")
        val views = gltf.getJSONArray("bufferViews")
        val meshes = gltf.getJSONArray("meshes")
        val nodes = gltf.getJSONArray("nodes")

        /** The numbers of accessor [index] as floats ([components] per element), or as ints for an index accessor. */
        fun slice(index: Int, components: Int): Triple<Int, Int, Int> {
            val accessor = accessors.getJSONObject(index)
            val view = views.getJSONObject(accessor.getInt("bufferView"))
            if (view.optInt("byteStride", 0) != 0) throw IllegalStateException("interleaved data is not supported")
            val start = binStart + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            if (start < binStart || start + count * components * 4 > binStart + binLength + 3) throw IllegalStateException("accessor outside the data")
            return Triple(start, count, accessor.getInt("componentType"))
        }

        fun floats(index: Int, components: Int): FloatArray {
            val (start, count, type) = slice(index, components)
            if (type != FLOAT) throw IllegalStateException("not floats")
            val out = FloatArray(count * components)
            val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            view.position(start)
            view.asFloatBuffer().get(out)
            return out
        }

        fun ints(index: Int): IntArray {
            val accessor = accessors.getJSONObject(index)
            val view = views.getJSONObject(accessor.getInt("bufferView"))
            val start = binStart + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            val out = IntArray(count)
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            when (accessor.getInt("componentType")) {
                UNSIGNED_SHORT -> for (i in 0 until count) out[i] = data.getShort(start + i * 2).toInt() and 0xFFFF
                UNSIGNED_INT -> for (i in 0 until count) out[i] = data.getInt(start + i * 4)
                else -> throw IllegalStateException("unsupported index type")
            }
            return out
        }

        val parts = ArrayList<GlbPart>()
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            val meshIndex = node.optInt("mesh", -1)
            val extras = node.optJSONObject("extras")
            if (meshIndex < 0 || extras == null) continue
            val primitive = meshes.getJSONObject(meshIndex).getJSONArray("primitives").getJSONObject(0)
            val attributes = primitive.getJSONObject("attributes")
            val positions = floats(attributes.getInt("POSITION"), 3)
            val normals = floats(attributes.getInt("NORMAL"), 3)
            val indices = ints(primitive.getInt("indices"))
            if (indices.any { it < 0 || it >= positions.size / 3 }) throw IllegalStateException("an index outside the vertices")
            val group = extras.optString("group")
            val kind = when (group) { "skeleton" -> PartKind.Skeleton; "skin" -> PartKind.Skin; else -> PartKind.Muscle }
            if (group.isEmpty()) continue
            parts += GlbPart(
                name = node.optString("name"),
                kind = kind,
                group = group,
                side = extras.optString("side", "C"),
                layer = if (extras.optString("layer") == "deep") MuscleLayer.Deep else MuscleLayer.Surface,
                positions = positions,
                normals = normals,
                indices = indices,
            )
        }
        return if (parts.isEmpty()) null else GlbModel(parts)
    }
}
