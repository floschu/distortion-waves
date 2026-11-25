package at.florianschuster.distortionwaves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

/**
 * Drives a [DistortionWave] by hand: send the wave out with [reveal], collapse it with [collapse],
 * flip it with [toggle], or run it over a single view with [play].
 *
 * Read [progress] or [elapsed] while drawing to change your own content in step with the wave.
 *
 * In a composable, create it with [rememberDistortionWaveState]. Use one state for one
 * [DistortionWave].
 *
 * @param initiallyRevealed true to start on the revealed content.
 */
@Stable
public class DistortionWaveState(
    initiallyRevealed: Boolean = false,
) {
    private val animatable = Animatable(if (initiallyRevealed) 1f else 0f)

    /** How far through its life the wave is, 0–1. 0 shows the content, 1 the revealed content. */
    public val progress: Float
        get() = animatable.value

    /** True while the wave is moving, out or back. */
    public val isRunning: Boolean
        get() = animatable.isRunning

    /**
     * True once [reveal] is called, until [collapse] is called. While the wave runs, this is the
     * side it is headed to.
     */
    public val revealed: Boolean
        get() = animatable.targetValue == 1f

    /** Seconds the whole wave takes with the current style. */
    public val duration: Float
        get() = style.duration

    /** Seconds into the wave: [progress] times [duration]. */
    public val elapsed: Float
        get() = progress * style.duration

    /**
     * Seconds after the start at which the front leaves the source. Before that, everything is
     * pulled toward the source.
     */
    public val launchTime: Float
        get() = style.anticipation / style.speed

    internal var style by mutableStateOf(DistortionWaveStyle.Vertical)
    internal var plain by mutableStateOf(false)

    /** The touch the current wave leans with, in root coordinates. */
    internal var focusInRoot by mutableStateOf<Offset?>(null)
    internal var sourceInRoot by mutableStateOf<Rect?>(null)
    private var lastPressInRoot: Offset? = null

    /**
     * Sends the wave out and returns once it has left and the edges have cleared. Does nothing if
     * the revealed content is already showing. While the wave collapses, it turns around and runs
     * out again from where it is.
     *
     * @param focus where the source was touched, in root coordinates. Without one, the last press
     *   on the element marked with [distortionWaveSource].
     */
    public suspend fun reveal(focus: Offset? = null) {
        if (animatable.value == 1f) return
        if (animatable.value == 0f) focusInRoot = focus ?: lastPressInRoot
        // Only the part of the wave that is left, so a turn mid-wave keeps its pace.
        val left = 1f - animatable.value
        animatable.animateTo(
            1f,
            tween(
                ((if (plain) PLAIN_SECONDS else style.duration) * left).millis(),
                easing = LinearEasing,
            ),
        )
    }

    /**
     * Collapses the wave back into its source, quicker than it opened. While the wave runs out, it
     * turns around from where it is.
     */
    public suspend fun collapse() {
        if (animatable.value == 0f) return
        val from = animatable.value
        val spec =
            when {
                plain -> tween<Float>((PLAIN_SECONDS * from).millis(), easing = LinearEasing)
                // From the revealed side, on the wave's own easing. It is quick
                // through the end of the wave's life, where little is on screen,
                // so the collapse answers the tap at once.
                from == 1f ->
                    tween((style.duration / style.returnSpeed).millis(), easing = style.easing)
                // Mid-wave, straight back at the return speed, so the wave
                // reverses without a jump in pace.
                else ->
                    tween(
                        (style.duration / style.returnSpeed * from).millis(),
                        easing = LinearEasing,
                    )
            }
        animatable.animateTo(0f, spec)
    }

    /** Collapses the wave if it is revealed or on its way out, and reveals it otherwise. */
    public suspend fun toggle(focus: Offset? = null) {
        if (revealed) collapse() else reveal(focus)
    }

    /**
     * Runs the wave once over a view that stays, then goes back to rest: for content that changes
     * in place while the wave passes over it. If another call takes over before the wave has
     * finished, the wave carries on from where it is.
     */
    public suspend fun play(focus: Offset? = null) {
        animatable.snapTo(0f)
        reveal(focus)
        // Not in a finally block: a snap from a cancelled run would stop the call that took over.
        animatable.snapTo(0f)
    }

    /** Jumps to the content or to the revealed content, without a wave. */
    public suspend fun snapTo(revealed: Boolean) {
        animatable.snapTo(if (revealed) 1f else 0f)
    }

    /** Holds the wave at `progress`, 0–1: for scrubbing, or a still frame. */
    public suspend fun scrubTo(progress: Float) {
        animatable.snapTo(progress.coerceIn(0f, 1f))
    }

    internal fun pressed(at: Offset) {
        lastPressInRoot = at
    }

    private fun Float.millis() = (this * 1000).toInt().coerceAtLeast(1)

    internal companion object {
        private const val PLAIN_SECONDS = 0.3f

        /** Saves which side shows. A wave cut off mid-way comes back on the side it was headed. */
        val Saver: Saver<DistortionWaveState, Boolean> =
            Saver(save = { it.revealed }, restore = { DistortionWaveState(it) })
    }
}

/**
 * Creates a [DistortionWaveState] and remembers it. The side on show survives a configuration
 * change and the app being restored.
 *
 * @param initiallyRevealed true to start on the revealed content.
 */
@Composable
public fun rememberDistortionWaveState(initiallyRevealed: Boolean = false): DistortionWaveState =
    rememberSaveable(saver = DistortionWaveState.Saver) { DistortionWaveState(initiallyRevealed) }

/**
 * Marks the element the wave opens from: a button, an edge handle, an avatar. The wave follows it
 * as it moves, and leans toward where it was last pressed.
 */
public fun Modifier.distortionWaveSource(state: DistortionWaveState): Modifier =
    this.onGloballyPositioned { state.sourceInRoot = Rect(it.positionInRoot(), it.size.toSize()) }
        .pointerInput(state) {
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.sourceInRoot?.let { state.pressed(it.topLeft + down.position) }
            }
        }
