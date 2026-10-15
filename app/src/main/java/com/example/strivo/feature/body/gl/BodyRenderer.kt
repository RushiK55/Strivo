package com.example.strivo.feature.body.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.example.strivo.domain.body.BodyCamera
import com.example.strivo.domain.body.BodyKey
import com.example.strivo.domain.body.CameraSpot
import com.example.strivo.domain.body.DrawState
import com.example.strivo.domain.body.GlbModel
import com.example.strivo.domain.body.GlbPart
import com.example.strivo.domain.body.Look
import com.example.strivo.domain.body.MuscleCatalogue
import com.example.strivo.domain.body.MuscleLook
import com.example.strivo.domain.body.PartKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Draws the 3D body with OpenGL ES 3.0, natively (no web view, no 3D library): the muscles, and over them a see-through skin that
 * fades away when a muscle is picked. The muscles the user trains glow and pulse gently; the picked one glows fully and the others
 * step back. It also answers which muscle is under a tap, by drawing the muscles once into an off-screen image where each has its
 * own flat colour and reading the pixel under the finger.
 *
 * Everything here runs on the GL thread. [BodyGlView] is the only caller and queues each call onto that thread.
 */
internal class BodyRenderer(
    private val catalogue: MuscleCatalogue,
    private val events: Events,
) : GLSurfaceView.Renderer {

    interface Events {
        /** A tap landed on a muscle (its id) or on nothing (null). */
        fun onPicked(group: String?)

        /** The model is on the graphics card and drawn. */
        fun onReady()

        fun onFailure(message: String)

        /** Whether frames are needed continuously (a flight, a fade, a pulse) or the picture can rest. */
        fun onAnimating(animating: Boolean)
    }

    /** One piece of the body on the graphics card. */
    private class GlMesh(
        val part: GlbPart,
        val vao: Int,
        val buffers: IntArray,
        val indexCount: Int,
        /** The number a tap on it is answered with (1 and up; 0 is "nothing" in the picking image). */
        val pickId: Int,
        /** The muscle's colour in linear light, with its small steady difference in lightness. */
        val baseColor: FloatArray,
    )

    private var program = 0
    private var uMvp = 0
    private var uNormalMatrix = 0
    private var uColor = 0
    private var uEmissive = 0
    private var uPick = 0

    private var meshes: List<GlMesh> = emptyList()
    private var body = BodyKey.Male
    private var drawState = DrawState()
    private var skinWanted = true
    private var skeletonOn = false
    private var skinOpacity = SKIN_OPACITY
    private var skinFade: Triple<Float, Float, Long>? = null // from, to, start (ms)

    private var camera: CameraSpot? = null
    private var fly: Triple<CameraSpot, CameraSpot, Long>? = null // from, to, start (ms)

    private var width = 1
    private var height = 1
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private val normalMatrix = FloatArray(9)

    private var pickFbo = 0
    private var pickTexture = 0
    private var pickDepth = 0
    private var pickWidth = 0
    private var pickHeight = 0

    private var animating = false

    /** The muscle the camera is on (null: the whole body), kept so a change of the view's size can frame it again. */
    private var focusGroup: String? = null

    /** Whether the view's size is known yet (the camera is framed from it) and whether a body is waiting to be framed. */
    private var sizeKnown = false
    private var needsFraming = false

    /** How each piece looks now, in the order of [meshes] (null for the skin, which is not a muscle); redone when what is shown changes. */
    private var looks: Array<Look?> = emptyArray()

    private val aspect: Float get() = width.toFloat() / max(height, 1)

    // ---- The surface ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            program = buildProgram()
            uMvp = GLES30.glGetUniformLocation(program, "uMvp")
            uNormalMatrix = GLES30.glGetUniformLocation(program, "uNormalMatrix")
            uColor = GLES30.glGetUniformLocation(program, "uColor")
            uEmissive = GLES30.glGetUniformLocation(program, "uEmissive")
            uPick = GLES30.glGetUniformLocation(program, "uPick")
        } catch (e: RuntimeException) {
            events.onFailure(e.message ?: "The graphics could not be set up.")
        }
        // A new surface starts with no graphics objects: the pieces and the picking image are made again (see setModel).
        meshes = emptyList()
        pickFbo = 0
        pickTexture = 0
        pickDepth = 0
        pickWidth = 0
        pickHeight = 0
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        modelKept?.let { setModel(it.first, it.second) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(width, 1)
        this.height = max(height, 1)
        sizeKnown = true
        GLES30.glViewport(0, 0, this.width, this.height)
        if (needsFraming && meshes.isNotEmpty()) {
            needsFraming = false
            showWhole(front = true, animate = false)
        } else if (camera != null) {
            // The view changed size (the card below it came or went, the phone turned): frame what is looked at again for the new shape,
            // keeping a flight that is under way.
            val flight = fly
            val spot = spotFor(focusGroup)
            if (flight != null && spot != null) fly = Triple(flight.first, spot, flight.third)
            else if (focusGroup != null && spot != null) camera = spot
        }
    }

    // ---- What the view asks for ----

    /** The model last given, kept so a new surface (the app was in the background) can put it on the graphics card again. */
    private var modelKept: Pair<GlbModel, BodyKey>? = null

    fun setModel(model: GlbModel, body: BodyKey) {
        modelKept = model to body
        if (program == 0) return // (onSurfaceCreated puts it up)
        releaseMeshes()
        looks = emptyArray()
        this.body = body
        try {
            var pickId = 1
            meshes = model.parts.map { part -> upload(part, if (part.kind == PartKind.Muscle) pickId++ else 0) }
        } catch (e: RuntimeException) {
            events.onFailure(e.message ?: "The body could not be put on the graphics card.")
            return
        }
        refreshLooks()
        if (sizeKnown) showWhole(front = true, animate = false) else needsFraming = true
        events.onReady()
    }

    fun setScene(state: DrawState, skin: Boolean, skeleton: Boolean = false) {
        drawState = state
        skeletonOn = skeleton
        refreshLooks()
        if (skin != skinWanted) {
            skinWanted = skin
            skinFade = Triple(skinOpacity, if (skin) SKIN_OPACITY else 0f, SystemClock.uptimeMillis())
        }
    }

    private fun refreshLooks() {
        looks = Array(meshes.size) { i ->
            val part = meshes[i].part
            if (part.kind != PartKind.Muscle) return@Array null
            val look = MuscleLook.lookOf(part.group, part.layer, drawState)
            // With the skeleton shown, the muscles that are not lit step back so the bones can be seen through them.
            if (skeletonOn && look.glow == 0f) look.copy(opacity = min(look.opacity, SKELETON_MUSCLE_OPACITY)) else look
        }
    }

    /** Where the camera goes to look at a muscle, or (null) at the whole body from the front. */
    private fun spotFor(group: String?): CameraSpot? {
        val place = group?.let { catalogue.group(it)?.placeOn(body) }
        return if (group != null && place != null) {
            BodyCamera.cameraFor(place.box, catalogue.group(group)!!.view, aspect = aspect)
        } else {
            catalogue.body(body)?.let { BodyCamera.bodyCamera(it.box, front = true, aspect = aspect) }
        }
    }

    /** Flies to look at a muscle, or (null) back to the whole body from the front. */
    fun focus(group: String?) {
        focusGroup = group
        val spot = spotFor(group) ?: return
        flyTo(spot, animate = camera != null)
    }

    /** The whole body from the front or the back. */
    fun showWhole(front: Boolean, animate: Boolean = true) {
        focusGroup = null
        val info = catalogue.body(body) ?: return
        flyTo(BodyCamera.bodyCamera(info.box, front, aspect = aspect), animate)
    }

    fun orbit(dAzimuth: Float, dElevation: Float) {
        camera = camera?.let { BodyCamera.orbit(it, dAzimuth, dElevation) }
        fly = null
    }

    fun zoom(factor: Float) {
        camera = camera?.let { BodyCamera.dolly(it, factor) }
        fly = null
    }

    fun pan(dx: Float, dy: Float) {
        camera = camera?.let { BodyCamera.pan(it, dx, dy) }
        fly = null
    }

    /** Answers which muscle is under the pixel ([x], [y]) of the view (from its top left), by drawing the muscles in flat colours. */
    fun pick(x: Float, y: Float) {
        val picked = pickAt(x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1))
        events.onPicked(picked)
    }

    // ---- Drawing ----

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) return
        val now = SystemClock.uptimeMillis()
        stepFly(now)
        stepSkin(now)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val spot = camera
        if (spot == null || meshes.isEmpty()) { rest(); return }
        setMatrices(spot)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix3fv(uNormalMatrix, 1, false, normalMatrix, 0)
        GLES30.glUniform1i(uPick, 0)

        val pulse = 0.8f + 0.2f * sin(now / 1000f * 2f * PI.toFloat() / 1.7f)
        // Solid muscles first (they write depth), then the ones stepping back, then the skin over everything.
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        for (i in meshes.indices) { val look = looks.getOrNull(i); if (look != null && look.opacity >= 0.999f) drawMuscle(meshes[i], look, pulse) }
        if (skeletonOn) {
            GLES30.glUniform4f(uColor, BONE_COLOR[0], BONE_COLOR[1], BONE_COLOR[2], 1f)
            GLES30.glUniform3f(uEmissive, 0f, 0f, 0f)
            for (mesh in meshes) if (mesh.part.kind == PartKind.Skeleton) draw(mesh)
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        for (i in meshes.indices) { val look = looks.getOrNull(i); if (look != null && look.opacity < 0.999f && look.opacity > 0.004f) drawMuscle(meshes[i], look, pulse) }
        if (skinOpacity > 0.004f) {
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glCullFace(GLES30.GL_BACK)
            for (mesh in meshes) if (mesh.part.kind == PartKind.Skin) {
                GLES30.glUniform4f(uColor, SKIN_COLOR[0], SKIN_COLOR[1], SKIN_COLOR[2], skinOpacity)
                GLES30.glUniform3f(uEmissive, 0f, 0f, 0f)
                draw(mesh)
            }
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // Frames are needed while something moves or pulses; otherwise the picture rests.
        val glowing = drawState.selected == null && !drawState.trained.isEmpty
        rest(fly != null || skinFade != null || glowing)
    }

    private fun rest(needFrames: Boolean = false) {
        if (needFrames != animating) {
            animating = needFrames
            events.onAnimating(needFrames)
        }
    }

    private fun drawMuscle(mesh: GlMesh, look: Look, pulse: Float) {
        val steady = mesh.part.group == drawState.selected
        val glow = look.glow * (if (steady) 1f else pulse)
        val dim = 1f - look.dim * 0.55f
        val mix = min(1f, glow * 0.6f)
        val r = mesh.baseColor[0] * dim * (1f - mix) + AMBER[0] * mix
        val g = mesh.baseColor[1] * dim * (1f - mix) + AMBER[1] * mix
        val b = mesh.baseColor[2] * dim * (1f - mix) + AMBER[2] * mix
        GLES30.glUniform4f(uColor, r, g, b, look.opacity)
        GLES30.glUniform3f(uEmissive, AMBER[0] * glow * 0.8f, AMBER[1] * glow * 0.8f, AMBER[2] * glow * 0.8f)
        draw(mesh)
    }

    private fun draw(mesh: GlMesh) {
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_INT, 0)
    }

    private fun setMatrices(spot: CameraSpot) {
        Matrix.perspectiveM(projection, 0, BodyCamera.FOV_DEGREES, aspect, 0.05f, 30f)
        Matrix.setLookAtM(view, 0, spot.position.x, spot.position.y, spot.position.z, spot.target.x, spot.target.y, spot.target.z, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        // The body is not moved or scaled, so a normal goes from the model to the view by the view's own turn.
        normalMatrix[0] = view[0]; normalMatrix[1] = view[1]; normalMatrix[2] = view[2]
        normalMatrix[3] = view[4]; normalMatrix[4] = view[5]; normalMatrix[5] = view[6]
        normalMatrix[6] = view[8]; normalMatrix[7] = view[9]; normalMatrix[8] = view[10]
    }

    // ---- The camera's flight and the skin's fade ----

    private fun flyTo(spot: CameraSpot, animate: Boolean) {
        val from = camera
        if (!animate || from == null) { camera = spot; fly = null; return }
        fly = Triple(from, spot, SystemClock.uptimeMillis())
    }

    private fun stepFly(now: Long) {
        val (from, to, start) = fly ?: return
        val t = min((now - start).toFloat() / FLY_MILLIS, 1f)
        val k = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
        camera = BodyCamera.blend(from, to, k)
        if (t >= 1f) fly = null
    }

    private fun stepSkin(now: Long) {
        val (from, to, start) = skinFade ?: return
        val t = min((now - start).toFloat() / SKIN_MILLIS, 1f)
        val k = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
        skinOpacity = from + (to - from) * k
        if (t >= 1f) skinFade = null
    }

    // ---- Picking ----

    private fun pickAt(x: Int, y: Int): String? {
        val spot = camera ?: return null
        if (program == 0 || meshes.isEmpty()) return null
        ensurePickTarget()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, pickFbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        setMatrices(spot)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix3fv(uNormalMatrix, 1, false, normalMatrix, 0)
        GLES30.glUniform1i(uPick, 1)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(true)
        for (i in meshes.indices) {
            val mesh = meshes[i]
            if (mesh.part.kind != PartKind.Muscle) continue // the skin is looked through
            if (looks.getOrNull(i)?.pickable != true) continue
            GLES30.glUniform4f(uColor, (mesh.pickId shr 16 and 255) / 255f, (mesh.pickId shr 8 and 255) / 255f, (mesh.pickId and 255) / 255f, 1f)
            draw(mesh)
        }
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(x, height - 1 - y, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glUniform1i(uPick, 0)
        val alpha = pixel.get(3).toInt() and 255
        if (alpha == 0) return null
        val id = ((pixel.get(0).toInt() and 255) shl 16) or ((pixel.get(1).toInt() and 255) shl 8) or (pixel.get(2).toInt() and 255)
        return meshes.firstOrNull { it.pickId == id }?.part?.group
    }

    private fun ensurePickTarget() {
        if (pickFbo != 0 && pickWidth == width && pickHeight == height) return
        if (pickFbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(pickFbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(pickTexture), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(pickDepth), 0)
        }
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        pickTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pickTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glGenRenderbuffers(1, ids, 0)
        pickDepth = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, pickDepth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, width, height)
        GLES30.glGenFramebuffers(1, ids, 0)
        pickFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, pickFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, pickTexture, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, pickDepth)
        pickWidth = width
        pickHeight = height
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    // ---- Graphics objects ----

    private fun upload(part: GlbPart, pickId: Int): GlMesh {
        val vertexCount = part.vertexCount
        val interleaved = ByteBuffer.allocateDirect(vertexCount * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (v in 0 until vertexCount) {
            interleaved.put(part.positions, v * 3, 3)
            interleaved.put(part.normals, v * 3, 3)
        }
        interleaved.position(0)
        val indices = ByteBuffer.allocateDirect(part.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        indices.put(part.indices).position(0)

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * 6 * 4, interleaved, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, part.indices.size * 4, indices, GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
        return GlMesh(part, vao, buffers, part.indices.size, pickId, muscleColor(part.group))
    }

    private fun releaseMeshes() {
        for (mesh in meshes) {
            GLES30.glDeleteBuffers(2, mesh.buffers, 0)
            GLES30.glDeleteVertexArrays(1, intArrayOf(mesh.vao), 0)
        }
        meshes = emptyList()
    }

    /** The muscle colour (a muted red) in linear light, a little lighter or darker for each muscle so neighbours can be told apart. */
    private fun muscleColor(group: String): FloatArray {
        val (h, s, l) = rgbToHsl(0xA8 / 255f, 0x5B / 255f, 0x50 / 255f)
        val rgb = hslToRgb(h, s, (l + MuscleLook.lightnessShift(group)).coerceIn(0f, 1f))
        return floatArrayOf(rgb[0].pow(2.2f), rgb[1].pow(2.2f), rgb[2].pow(2.2f))
    }

    // ---- Shaders ----

    private fun buildProgram(): Int {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT)
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) throw RuntimeException("The graphics program did not link: " + GLES30.glGetProgramInfoLog(id))
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        return id
    }

    private fun compile(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) throw RuntimeException("A shader did not compile: " + GLES30.glGetShaderInfoLog(id))
        return id
    }

    private companion object {
        const val FLY_MILLIS = 800L
        const val SKIN_MILLIS = 350L

        /** How solid the skin is: see-through enough for the muscles (and what glows) to show, solid enough to read as a body. */
        const val SKIN_OPACITY = 0.32f

        /** How solid the muscles that are not lit are while the skeleton is shown. */
        const val SKELETON_MUSCLE_OPACITY = 0.22f

        /** The highlight colour (the app's amber, #FFB800) and the skin's (#9AA5BD), in linear light. */
        val AMBER = floatArrayOf(1f, 0.7216f.pow(2.2f), 0f)
        val BONE_COLOR = floatArrayOf(0.89f.pow(2.2f), 0.86f.pow(2.2f), 0.78f.pow(2.2f))
        val SKIN_COLOR = floatArrayOf(0.6039f.pow(2.2f), 0.6471f.pow(2.2f), 0.7412f.pow(2.2f))

        const val VERTEX = """#version 300 es
uniform mat4 uMvp;
uniform mat3 uNormalMatrix;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
out vec3 vNormal;
void main() {
    vNormal = uNormalMatrix * aNormal;
    gl_Position = uMvp * vec4(aPos, 1.0);
}"""

        const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec4 uColor;
uniform vec3 uEmissive;
uniform int uPick;
in vec3 vNormal;
out vec4 fragColor;
void main() {
    if (uPick == 1) { fragColor = uColor; return; }
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    vec3 key = normalize(vec3(0.45, 0.65, 1.0));
    float diffuse = max(dot(n, key), 0.0);
    float sky = 0.5 + 0.5 * n.y;
    vec3 lit = uColor.rgb * (0.36 + 0.30 * sky + 0.85 * diffuse) + uEmissive;
    fragColor = vec4(pow(clamp(lit, 0.0, 1.0), vec3(1.0 / 2.2)), uColor.a);
}"""
    }
}

// ---- Colour helpers (kept here: only the renderer needs them) ----

private fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    if (hi == lo) return Triple(0f, 0f, l)
    val d = hi - lo
    val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
    val h = when (hi) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f
    return Triple(h, s, l)
}

private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) return floatArrayOf(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return floatArrayOf(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f))
}
