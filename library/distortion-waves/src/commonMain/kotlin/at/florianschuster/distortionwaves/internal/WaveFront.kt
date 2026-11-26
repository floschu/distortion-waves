package at.florianschuster.distortionwaves.internal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import at.florianschuster.distortionwaves.DistortionWaveStyle
import at.florianschuster.distortionwaves.DistortionWaveType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the wave is at a moment of its life: a pure function of progress, so the wave can be
 * scrubbed and reversed.
 *
 * In order: gravity pulls everything toward the source. The front launches off the source's
 * outline, crosses the screen on the style's easing and settles past the far side as its swell,
 * blur and colour die away. The colour it left on the edges is wiped away by a second, later front.
 *
 * Everything here is in points: [source], [size] and [focus] have already been divided by the
 * pixels in a point.
 */
internal class WaveFront(
    progress: Float,
    val source: Rect,
    size: Size,
    style: DistortionWaveStyle,
    focus: Offset?,
    /** True for a horizontal wave in a right-to-left layout: it travels right to left. */
    val mirrored: Boolean = false,
) {
    val width = size.width
    val height = size.height
    val originX: Float
    val originY: Float

    /** How far the wave's frame is turned, radians: positive leans it right. */
    val tilt: Float

    /** Half-length of the straight part of the pill the front is an offset of. */
    val spine: Float

    /** Distance of the front from that spine. */
    val radius: Float

    /** How far the front is along its way, 0–1. */
    val moved: Float

    /** Scale of the x axis in the field: above 1 the front bows. */
    val squash: Float
    val time: Float

    /** How strongly the swell and the blur act, 0–1. */
    val energy: Float
    val swell: Float

    /** How far content is pulled toward the source. */
    val gravity: Float

    /** How strong the colour on the front is, 0–1. */
    val glow: Float

    /** Distance of the later front that wipes the colour off the edges. */
    val clearRadius: Float

    /** Distance of the front that wipes the hairline off them, later still. */
    val hairClearRadius: Float

    /** Opacity of the colour on the edges, 0–1. */
    val rim: Float

    /** How far the particles have faded in, 0–1. */
    val sparkle: Float

    /** Seconds since the front left the source, 0 before that. */
    val flying: Float
    val launched: Boolean

    /** True once the front has crossed the whole screen. */
    val hasCleared: Boolean
    val ring = style.type == DistortionWaveType.Center
    val sideways = style.type == DistortionWaveType.Horizontal

    /** Radius of the source the wave leaves alone: a ring's, 0 for an arc. */
    val hold: Float

    init {
        val t = progress.coerceIn(0f, 1f) * style.timeline
        val sinceLaunch = t - style.anticipation.duration
        val gathered = smooth(0f, max(style.anticipation.duration, 0.001f), t)
        val x = (sinceLaunch / style.travel).coerceIn(0f, 1f)
        moved = style.travelEasing.clamped(x)

        val fullSquash = if (ring) 1f else ARC_SQUASH
        val startRadius = min(source.width, source.height) / 2f
        // The source's long side, along which the front starts as a pill.
        val length = if (sideways) source.height else source.width
        val startSpine = if (ring) 0f else max(0f, length / 2f - startRadius)
        // The horizontal wave is the vertical one turned a quarter: clockwise
        // to travel right, the other way to travel left.
        val quarter = (PI / 2).toFloat()
        val turned = if (!sideways) 0f else if (mirrored) -quarter else quarter
        val axisX = cos(turned)
        val axisY = sin(turned)

        // Touched off-centre, the wave opens from under the finger and leans
        // its way.
        val lean =
            if (ring || focus == null) {
                0f
            } else {
                val off = if (sideways) focus.y - source.center.y else focus.x - source.center.x
                // Turned the other way, the same touch leans the frame the other way.
                (off / max(length / 2f, 1f)).coerceIn(-1f, 1f) * (if (mirrored) -1f else 1f)
            }
        val fullShift = lean * length / 2f * style.lean
        val fullLean = lean * style.lean * MAX_TILT
        val fullTilt = turned + fullLean
        val opening = smooth(0f, 0.45f, moved)

        // The radius at which the front has left the farthest corner, in the
        // wave's own frame once it has finished leaning.
        val cs = cos(fullTilt)
        val sn = sin(fullTilt)
        var reach = 0f
        for (cornerX in floatArrayOf(0f, width)) {
            for (cornerY in floatArrayOf(0f, height)) {
                val dx = cornerX - source.center.x - fullShift * axisX
                val dy = cornerY - source.center.y - fullShift * axisY
                reach = max(reach, hypot((cs * dx + sn * dy) * fullSquash, -sn * dx + cs * dy))
            }
        }
        // …and has taken the outgoing side with it.
        val crossed = reach + (style.lens.bend + COVER_ZONE) * fullSquash
        val settling = 1f - smooth(SETTLE_START, SETTLE_END, x)

        // The lean turns in over the first tenth of the move.
        val facing = smooth(0f, 0.1f, moved)
        originX = source.center.x + fullShift * facing * axisX
        originY = source.center.y + fullShift * facing * axisY
        tilt = turned + fullLean * facing
        // The pill opens into an arc only as the front travels.
        spine = startSpine * (1f - opening)
        // Before the launch a ring of colour falls in, onto where the front
        // will start.
        val launchRadius = startRadius + max(style.launchGap, 0f)
        radius =
            if (sinceLaunch < 0f) {
                launchRadius + style.anticipation.inhale * (1f - gathered)
            } else {
                launchRadius + (crossed + OVERSHOOT - launchRadius) * moved
            }
        squash = 1f + (fullSquash - 1f) * smooth(0f, 0.5f, moved)
        time = t
        energy = smooth(0f, 0.05f, x) * settling
        swell = style.lens.bend * smooth(0f, 0.3f, moved)
        gravity = style.anticipation.pull * gathered * (1f - smooth(0f, RELEASE, sinceLaunch))
        // From what has gathered on the source, so nothing pops at the launch.
        val gatheredGlow = min(GATHERED_GLOW * max(style.anticipation.glow, 0f), 1f)
        glow =
            if (sinceLaunch < 0f) {
                gatheredGlow * gathered
            } else {
                (gatheredGlow + (1f - gatheredGlow) * smooth(0f, 0.07f, x)) * settling
            }
        val clearStart = startRadius - style.rim.softness
        val clearEnd = reach + (style.rim.softness + style.lens.bend) * fullSquash
        clearRadius =
            clearStart +
                (clearEnd - clearStart) *
                    style.rim.easing.clamped(
                        (sinceLaunch - style.rim.delay) / max(style.rim.fade, 0.01f),
                    )
        hairClearRadius =
            clearStart +
                (clearEnd - clearStart) *
                    style.rim.easing.clamped(
                        (sinceLaunch - style.rim.delay - style.hairline.delay) /
                            max(style.hairline.fade, 0.01f),
                    )
        rim = style.rim.opacity * smooth(0f, 0.1f, x) * (1f - smooth(0.9f, 1f, t / style.timeline))
        sparkle = smooth(0f, max(style.anticipation.duration, 0.001f) * 0.35f, t)
        flying = max(sinceLaunch, 0f)
        launched = sinceLaunch > 0f
        hasCleared = radius > crossed
        hold = if (ring) startRadius else 0f
    }

    companion object {
        /** Half the zone over which the two screens crossfade (COVER in the shaders). */
        const val COVER_ZONE = 60f

        /** The most an arc's x axis is ever squashed. A ring's never is. */
        private const val ARC_SQUASH = 1.4f

        /** Seconds gravity takes to let go after the launch. */
        private const val RELEASE = 0.22f
        private const val GATHERED_GLOW = 0.5f

        /** How far past having crossed the screen the front settles. */
        private const val OVERSHOOT = 100f

        /** How far the wave turns when touched at the end of the source: about 26°. */
        private const val MAX_TILT = 0.45f

        /** The part of the travel over which the swell, the blur and the colour die away. */
        private const val SETTLE_START = 0.2f
        private const val SETTLE_END = 0.55f

        fun smooth(
            a: Float,
            b: Float,
            x: Float,
        ): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun Easing.clamped(x: Float): Float =
            when {
                x <= 0f -> 0f
                x >= 1f -> 1f
                else -> transform(x)
            }
    }
}
