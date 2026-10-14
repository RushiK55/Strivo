package com.example.strivo.domain.body

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Where the camera is, and the point it looks at. */
data class CameraSpot(val target: Vec3, val position: Vec3) {
    /** How far the camera is from the point it looks at. */
    val distance: Float get() = (position - target).length()
}

/** The same rules as the dashboard's 3D body (admin/src/lib/muscles.ts), so the muscle fills the view the same way. */
object BodyCamera {
    const val FOV_DEGREES = 35f
    const val MIN_DISTANCE = 0.3f
    const val MAX_DISTANCE = 6f

    /** The widest a view can be, used so a very narrow view does not put the camera infinitely far. */
    private const val NARROWEST_ASPECT = 0.2f

    /** The least distance to a muscle, so a tiny one is not looked at from inside. */
    private const val CLOSEST_TO_MUSCLE = 0.45f

    private val Directions = mapOf(
        MuscleView.Front to Vec3(0.22f, 0.1f, 1f),
        MuscleView.Back to Vec3(-0.22f, 0.1f, -1f),
        MuscleView.Both to Vec3(1f, 0.12f, 0.55f),
    )

    private fun fit(fov: Float, aspect: Float): Pair<Float, Float> {
        val vertical = fov * PI.toFloat() / 180f / 2f
        val horizontal = atan(tan(vertical) * max(aspect, NARROWEST_ASPECT))
        return vertical to horizontal
    }

    /**
     * Where the camera goes to look at [box]: far enough back for all of it to fit with some air around it, from the side the
     * muscle is best seen from. [fov] is the vertical field of view in degrees and [aspect] the width over the height of the view.
     */
    fun cameraFor(box: Box, view: MuscleView, fov: Float = FOV_DEGREES, aspect: Float): CameraSpot {
        val target = box.center
        val radius = box.size.length() / 2f
        val (vertical, horizontal) = fit(fov, aspect)
        val distance = max(radius * 1.6f / sin(min(vertical, horizontal)), CLOSEST_TO_MUSCLE)
        val direction = Directions.getValue(view).normalised()
        return CameraSpot(target, target + direction * distance)
    }

    /**
     * The whole body from straight in front (or behind), as the starting view: far enough back for its height (or, in a narrow
     * view, its width) to fit with a margin, and looking at its middle.
     */
    fun bodyCamera(box: Box, front: Boolean, fov: Float = FOV_DEGREES, aspect: Float): CameraSpot {
        val target = box.center
        val size = box.size
        val tanHalf = tan(fov * PI.toFloat() / 180f / 2f)
        val distance = max(size.y / 2f / tanHalf, size.x / 2f / (tanHalf * max(aspect, NARROWEST_ASPECT))) * 1.1f + size.z / 2f
        val direction = (if (front) Vec3(0f, 0.03f, 1f) else Vec3(0f, 0.03f, -1f)).normalised()
        return CameraSpot(target, target + direction * distance)
    }

    /**
     * The camera [k] of the way (0 to 1) from [from] to [to]: the point it looks at moves in a straight line, but the camera swings
     * round it (the shorter way, with its height angle and distance changing smoothly) instead of cutting through the body, as it
     * would going from the front to a muscle on the back.
     */
    fun blend(from: CameraSpot, to: CameraSpot, k: Float): CameraSpot {
        fun mix(a: Float, b: Float) = a + (b - a) * k
        val target = Vec3(mix(from.target.x, to.target.x), mix(from.target.y, to.target.y), mix(from.target.z, to.target.z))
        val a = from.position - from.target
        val b = to.position - to.target
        val la = a.length()
        val lb = b.length()
        fun turn(v: Vec3) = atan2(v.x, v.z)
        fun lift(v: Vec3, l: Float) = asin((v.y / (if (l == 0f) 1f else l)).coerceIn(-1f, 1f))
        var swing = turn(b) - turn(a)
        val twoPi = (2 * PI).toFloat()
        swing = ((((swing + PI.toFloat()) % twoPi) + twoPi) % twoPi) - PI.toFloat() // the shorter way round, -180 to 180 degrees
        val around = turn(a) + swing * k
        val up = mix(lift(a, la), lift(b, lb))
        val distance = mix(la, lb)
        val position = Vec3(
            target.x + distance * sin(around) * cos(up),
            target.y + distance * sin(up),
            target.z + distance * cos(around) * cos(up),
        )
        return CameraSpot(target, position)
    }

    /** Turns the camera round what it looks at: [dAzimuth] sideways and [dElevation] up or down, both in radians; it never goes over the poles. */
    fun orbit(spot: CameraSpot, dAzimuth: Float, dElevation: Float): CameraSpot {
        val offset = spot.position - spot.target
        val distance = offset.length()
        val azimuth = atan2(offset.x, offset.z) + dAzimuth
        val elevation = (asin((offset.y / (if (distance == 0f) 1f else distance)).coerceIn(-1f, 1f)) + dElevation).coerceIn(-MAX_ELEVATION, MAX_ELEVATION)
        return CameraSpot(
            spot.target,
            Vec3(
                spot.target.x + distance * sin(azimuth) * cos(elevation),
                spot.target.y + distance * sin(elevation),
                spot.target.z + distance * cos(azimuth) * cos(elevation),
            ),
        )
    }

    /** Moves the camera toward (factor below 1) or away from (above 1) what it looks at, within [MIN_DISTANCE] and [MAX_DISTANCE]. */
    fun dolly(spot: CameraSpot, factor: Float): CameraSpot {
        val offset = spot.position - spot.target
        val distance = offset.length()
        if (distance == 0f) return spot
        val wanted = (distance * factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        return CameraSpot(spot.target, spot.target + offset * (wanted / distance))
    }

    /**
     * Slides the camera and the point it looks at together across the view: [dx] to the right and [dy] up, as shares of the
     * height of the view that is in sight at the target's distance (so a drag of the whole screen moves the body by one screen).
     */
    fun pan(spot: CameraSpot, dx: Float, dy: Float, fov: Float = FOV_DEGREES): CameraSpot {
        val offset = spot.position - spot.target
        val forward = (spot.target - spot.position).normalised()
        val worldUp = Vec3(0f, 1f, 0f)
        val right = Vec3(forward.y * worldUp.z - forward.z * worldUp.y, forward.z * worldUp.x - forward.x * worldUp.z, forward.x * worldUp.y - forward.y * worldUp.x).normalised()
        val up = Vec3(right.y * forward.z - right.z * forward.y, right.z * forward.x - right.x * forward.z, right.x * forward.y - right.y * forward.x).normalised()
        val seen = 2f * offset.length() * tan(fov * PI.toFloat() / 180f / 2f)
        val move = right * (-dx * seen) + up * (dy * seen)
        val target = spot.target + move
        return CameraSpot(target, spot.position + move).let { if (it.target.y < 0f || it.target.y > 2.2f) spot else it } // not off the body
    }

    /** The highest and lowest the camera may look from, just short of straight up or down. */
    private const val MAX_ELEVATION = 1.35f
}
