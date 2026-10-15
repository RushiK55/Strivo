package com.example.strivo.feature.body.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.strivo.domain.body.BodyKey
import com.example.strivo.domain.body.DrawState
import com.example.strivo.domain.body.GlbModel
import com.example.strivo.domain.body.MuscleCatalogue

/**
 * The 3D body as a native view. One finger turns it, two fingers pinch to zoom and slide to move it, a tap picks the muscle under
 * the finger. Everything it is told is passed on to the GL thread; what happens in the picture is reported back on the main thread.
 */
@SuppressLint("ViewConstructor")
class BodyGlView(
    context: Context,
    catalogue: MuscleCatalogue,
    private val listener: Listener,
) : GLSurfaceView(context) {

    interface Listener {
        /** A tap landed on a muscle (its id), or on nothing (null). */
        fun onPicked(group: String?)

        /** The model is on screen. */
        fun onReady()

        /** The graphics could not be used; [message] says why. */
        fun onFailure(message: String)
    }

    private val renderer = BodyRenderer(
        catalogue,
        object : BodyRenderer.Events {
            override fun onPicked(group: String?) { post { listener.onPicked(group) } }
            override fun onReady() { post { listener.onReady() }; requestRender() }
            override fun onFailure(message: String) { post { listener.onFailure(message) } }
            // Frames are drawn continuously while something moves or pulses, and only when something changes otherwise.
            override fun onAnimating(animating: Boolean) { post { renderMode = if (animating) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY; if (!animating) requestRender() } }
        },
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ---- What the screen asks for ----

    fun setModel(model: GlbModel, body: BodyKey) = run { queueEvent { renderer.setModel(model, body) }; requestRender() }

    fun setScene(state: DrawState, skin: Boolean, skeleton: Boolean = false) = run { queueEvent { renderer.setScene(state, skin, skeleton) }; requestRender() }

    /** Flies to a muscle, or (null) back to the whole body. */
    fun focus(group: String?) = run { queueEvent { renderer.focus(group) }; requestRender() }

    fun showWhole(front: Boolean) = run { queueEvent { renderer.showWhole(front) }; requestRender() }

    // ---- Touch ----

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastX = 0f
            private var lastY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                lastX = detector.focusX
                lastY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = 1f / detector.scaleFactor
                val dx = (detector.focusX - lastX) / height.coerceAtLeast(1)
                val dy = -(detector.focusY - lastY) / height.coerceAtLeast(1)
                lastX = detector.focusX
                lastY = detector.focusY
                queueEvent { renderer.zoom(factor); renderer.pan(dx, dy) }
                requestRender()
                return true
            }
        },
    )

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // Dragging right turns the body toward the finger: the camera goes the other way round it.
                val dAzimuth = distanceX * TURN_PER_PIXEL
                val dElevation = -distanceY * TURN_PER_PIXEL
                queueEvent { renderer.orbit(dAzimuth, dElevation) }
                requestRender()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                queueEvent { renderer.pick(x, y) }
                return true
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress && event.pointerCount == 1) gestures.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(true) // a scrolling screen around the body must not steal a turn
        return true
    }

    private companion object {
        /** Radians the body turns for each pixel a finger moves. */
        const val TURN_PER_PIXEL = 0.0065f
    }
}
