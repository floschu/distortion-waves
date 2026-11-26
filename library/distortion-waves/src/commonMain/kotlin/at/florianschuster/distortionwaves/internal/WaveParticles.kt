package at.florianschuster.distortionwaves.internal

import androidx.compose.ui.geometry.Offset
import at.florianschuster.distortionwaves.DistortionWaveStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One tiny white dot. Where it is follows from the front alone, so it scrubs and reverses with the
 * rest.
 *
 * During the anticipation it makes short hops toward the source. When the front leaves, the dot is
 * thrown out with it: the quickest ride the front, the slowest trail far behind. Then each one
 * floats on, swaying and twinkling, until it fades out.
 */
internal class WaveParticle(
    index: Int,
    style: DistortionWaveStyle,
) {
    private val index = (index + 1).toDouble()

    /** Where across the source it leaves from, -1…1. */
    private val column: Float

    /** Which way it leaves a ring, radians. */
    private val heading: Float

    /** Its share of the front's way: 1 rides the front, less trails behind it. */
    private val pace: Float

    /** How fast it keeps rising once the front has slowed down, pt/s. */
    private val drift: Float
    private val sway: Float
    private val swayRate: Float
    private val twinkleRate: Float
    private val phase: Float

    /** Seconds after the launch by which it has faded out. */
    private val life: Float

    /** Its diameter, pt. */
    val size: Float
    private val brightness: Float

    /** Seconds each hop toward the source lasts. */
    private val hop: Float
    private val hopOffset: Float

    private val reach = style.particles.reach
    private val flight = style.particles.flight
    private val gap = style.particles.gap
    private val anticipation = style.anticipation.duration
    private val gatherDots = style.anticipation.dots
    private val spread = style.particles.spread.coerceIn(0f, 1f)

    init {
        // Low-discrepancy sequences: evenly scattered, the same on every run.
        fun scatter(step: Double): Float = ((this.index * step) % 1.0).toFloat()
        column = (scatter(0.7548776662) * 2f - 1f) * 0.92f
        heading = scatter(0.7548776662) * 2f * PI.toFloat()
        // More of them toward the front, the rest thinning out behind it.
        pace = 0.35f + 0.72f * sqrt(scatter(0.8191725134))
        drift = 15f + 50f * scatter(0.6710436067)
        sway = 3f + 11f * scatter(0.4142135624)
        swayRate = 2.5f + 3.5f * scatter(0.7320508076)
        twinkleRate = 8f + 12f * scatter(0.2360679775)
        phase = scatter(0.6180339887) * 2f * PI.toFloat()
        life = style.particles.life * (0.4f + 0.6f * scatter(0.6457513111))
        val small = scatter(0.5497004779)
        size = style.particles.size * (0.55f + 0.45f * small * small)
        brightness = 0.3f + 0.7f * scatter(0.6055512755)
        hop = 0.16f + 0.14f * scatter(0.1231056256)
        hopOffset = scatter(0.4721359550)
    }

    fun point(front: WaveFront): Offset =
        when {
            front.ring && front.launched -> ringOutflow(front)
            front.ring -> ringInflow(front)
            front.launched -> outflow(front)
            else -> inflow(front)
        }

    fun opacity(front: WaveFront): Float {
        val twinkle = 0.5f + 0.5f * sin(front.time * twinkleRate + phase)
        val glint = brightness * (0.7f + 0.3f * twinkle)
        if (!front.launched) {
            // In and out within each hop, and all of them gone by the launch.
            val along = hopAlong(front.time)
            val hopping =
                WaveFront.smooth(0f, 0.25f, along) * (1f - WaveFront.smooth(0.55f, 1f, along))
            val ending = 1f - WaveFront.smooth(anticipation * 0.75f, anticipation, front.time)
            return front.sparkle * hopping * ending * glint * gatherDots
        }
        val burning = 1f - WaveFront.smooth(life * 0.55f, life, front.flying)
        return burning * glint
    }

    private fun hopCount(time: Float): Double = floor((time / hop + hopOffset).toDouble())

    /** How far through its current hop it is, 0–1. */
    private fun hopAlong(time: Float): Float {
        val hops = time / hop + hopOffset
        return hops - floor(hops)
    }

    /** In the source's own frame: `along` its long side, `out` the way the wave will travel. */
    private fun onSource(
        front: WaveFront,
        along: Float,
        out: Float,
    ): Offset {
        val source = front.source
        return when {
            !front.sideways -> Offset(source.center.x + along, source.center.y - out)
            front.mirrored -> Offset(source.center.x - out, source.center.y + along)
            else -> Offset(source.center.x + out, source.center.y + along)
        }
    }

    private fun inflow(front: WaveFront): Offset {
        val source = front.source
        val count = hopCount(front.time)
        val along = hopAlong(front.time)
        val half = (if (front.sideways) source.height else source.width) / 2f
        val face = (if (front.sideways) source.width else source.height) / 2f
        // Born somewhere off the source's face, never nearer than the hop and the gap.
        val x = (hash(index, count, 1.0) * 2f - 1f) * (half + 24f)
        val nearest = gap + flight
        val away = nearest + max(reach - nearest, 0f) * hash(index, count, 2.0)
        val start = onSource(front, x, face + away)
        // Flying for the nearest point of the source's face, faster and faster.
        val corner = min(source.width, source.height) / 2f
        val target = onSource(front, min(max(x, -half + corner), half - corner), face)
        val d = target - start
        val length = max(hypot(d.x, d.y), 1f)
        return start + d / length * (flight * along * along)
    }

    private fun outflow(front: WaveFront): Offset {
        val source = front.source
        val start = min(source.width, source.height) / 2f
        val widening = 1f + FAN * front.moved * pace * spread
        val swaying = sway * sin(swayRate * front.flying + phase) * min(front.flying / 0.25f, 1f)
        val x =
            column * (if (front.sideways) source.height else source.width) / 2f * widening + swaying
        val height =
            (start + (front.radius - start) * pace * spread) * (1f - BOW * column * column) +
                drift * front.flying * spread
        // Into the wave's frame, which leans with the touch.
        val cs = cos(front.tilt)
        val sn = sin(front.tilt)
        return Offset(front.originX + cs * x + sn * height, front.originY + sn * x - cs * height)
    }

    /** A hop toward a round source's centre from somewhere round it. */
    private fun ringInflow(front: WaveFront): Offset {
        val source = front.source
        val count = hopCount(front.time)
        val along = hopAlong(front.time)
        val angle = hash(index, count, 1.0) * 2f * PI.toFloat()
        val nearest = min(source.width, source.height) / 2f + gap + flight
        val away = nearest + max(reach - nearest, 0f) * hash(index, count, 2.0)
        val distance = away - flight * along * along
        return Offset(
            source.center.x + cos(angle) * distance,
            source.center.y + sin(angle) * distance,
        )
    }

    /** Thrown straight out with the ring, swaying across its way. */
    private fun ringOutflow(front: WaveFront): Offset {
        val edge = min(front.source.width, front.source.height) / 2f
        val distance = edge + ((front.radius - edge) * pace + drift * front.flying) * spread
        val swaying = sway * sin(swayRate * front.flying + phase) * min(front.flying / 0.25f, 1f)
        val dx = cos(heading)
        val dy = sin(heading)
        return Offset(
            front.originX + dx * distance - dy * swaying,
            front.originY + dy * distance + dx * swaying,
        )
    }

    private companion object {
        /** How much wider than the source the quickest end up: the front is an arc, opening. */
        const val FAN = 0.22f

        /** How much lower they fly at the source's ends than in its middle, as the front does. */
        const val BOW = 0.3f

        fun hash(
            i: Double,
            n: Double,
            salt: Double,
        ): Float {
            val x = sin(i * 12.9898 + n * 78.233 + salt * 37.719) * 43758.5453
            return (x - floor(x)).toFloat()
        }
    }
}

/**
 * The lumps that make the colour on the edges uneven: seven of them round the perimeter, each with
 * its own length and depth, drifting along the edge and breathing. Worked out once a frame rather
 * than for every pixel.
 */
internal object WaveRim {
    private const val COUNT = 7

    /** Centre along the perimeter, length and depth of each lump, flattened. */
    fun lumps(
        time: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        pulse: Float,
        out: FloatArray,
    ): FloatArray {
        val ex = max(width / 2f - cornerRadius, 0f)
        val ey = max(height / 2f - cornerRadius, 0f)
        val perimeter = 4f * (ex + ey + 0.5f * PI.toFloat() * cornerRadius)
        for (i in 0 until COUNT) {
            val fi = i.toDouble()
            // Fixed per lump, so the pattern is the same on every run.
            val a = hash(fi, 12.9898, 1.3)
            val b = hash(fi, 78.233, 4.1)
            val c = hash(fi, 39.425, 2.7)
            val centre =
                perimeter * (i + 0.35f * a) / COUNT + time * (-22f + 44f * b) * (1f + 2f * pulse)
            val length = perimeter / COUNT * (0.2f + 0.25f * c)
            val breath =
                (0.25f + 0.3f * pulse) * sin(time * (1.1f + 1.2f * a) * (1f + pulse) + i * 2.4f)
            val depth = (0.2f + 2.6f * b) * (1f + breath)
            out[i * 3] = centre
            out[i * 3 + 1] = length
            out[i * 3 + 2] = depth
        }
        return out
    }

    private fun hash(
        i: Double,
        a: Double,
        b: Double,
    ): Float {
        val x = sin(i * a + b) * 43758.5453
        return (x - floor(x)).toFloat()
    }
}
