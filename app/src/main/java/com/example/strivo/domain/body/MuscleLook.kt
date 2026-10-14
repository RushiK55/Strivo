package com.example.strivo.domain.body

/** The muscles a day of training works: the ones it trains mainly and the ones that help. A muscle is in one list, not both. */
data class TrainedMuscles(val main: Set<String>, val helper: Set<String>) {
    val isEmpty: Boolean get() = main.isEmpty() && helper.isEmpty()

    fun roleOf(id: String): MuscleRole? = when (id) {
        in main -> MuscleRole.Main
        in helper -> MuscleRole.Helper
        else -> null
    }

    companion object {
        val None = TrainedMuscles(emptySet(), emptySet())
    }
}

/** How a muscle is used by the training being shown. */
enum class MuscleRole { Main, Helper }

/** What decides how each muscle is drawn: the muscle picked (if any), whether the surface layer is faded, and what the user trains. */
data class DrawState(
    val selected: String? = null,
    val fade: Boolean = false,
    val trained: TrainedMuscles = TrainedMuscles.None,
)

/**
 * How one muscle is drawn. [opacity] 1 is solid; [glow] is how strongly it lights up in the highlight colour (0 none, 1 fully);
 * [dim] darkens it (the muscles that are not trained, so the trained ones stand out); [pickable] says whether a tap can land on it.
 */
data class Look(val opacity: Float, val glow: Float, val dim: Float, val pickable: Boolean)

/** The same rules as the dashboard's body (admin/src/lib/muscles.ts), plus the glow of the muscles the user trains. */
object MuscleLook {
    const val GLOW_SELECTED = 0.9f
    const val GLOW_MAIN = 0.7f
    const val GLOW_HELPER = 0.34f

    /** How much darker a muscle is when others are being trained and it is not one of them. */
    const val DIM_UNTRAINED = 0.45f

    /** A muscle can be tapped when it can be seen: with the surface faded only the deep muscles can. */
    fun isPickable(layer: MuscleLayer, fade: Boolean): Boolean = !(fade && layer == MuscleLayer.Surface)

    /**
     * How one muscle looks. The selected one is solid and glows strongly; the others step back so it stands out (more so when the
     * surface layer is faded and the muscle is a surface one). With nothing selected the surface layer is solid, or faint when
     * faded, the deep layer is solid, the muscles being trained glow (the main ones more than the helpers) and, when something
     * is being trained, the rest are a little darker.
     */
    fun lookOf(id: String, layer: MuscleLayer, state: DrawState): Look {
        val faded = state.fade && layer == MuscleLayer.Surface
        if (state.selected == id) return Look(opacity = 1f, glow = GLOW_SELECTED, dim = 0f, pickable = true)
        if (state.selected != null) return Look(opacity = if (faded) 0.07f else 0.3f, glow = 0f, dim = 0f, pickable = isPickable(layer, state.fade))
        val role = state.trained.roleOf(id)
        val glow = when (role) {
            MuscleRole.Main -> GLOW_MAIN
            MuscleRole.Helper -> GLOW_HELPER
            null -> 0f
        }
        val dim = if (role == null && !state.trained.isEmpty) DIM_UNTRAINED else 0f
        return Look(opacity = if (faded) 0.14f else 1f, glow = glow, dim = dim, pickable = !faded)
    }

    /** A small steady difference in lightness per muscle (-0.06 to +0.06), so neighbours of one colour can still be told apart. */
    fun lightnessShift(id: String): Float {
        var h = 0L
        for (ch in id) h = (h * 31 + ch.code) and 0xFFFFFFFFL
        return ((h % 13).toFloat() / 12f - 0.5f) * 0.12f
    }
}
