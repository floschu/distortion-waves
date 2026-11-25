package at.florianschuster.distortionwaves

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.cbrt
import kotlin.math.pow

/** Whether the wave gives way to a plain crossfade. */
public enum class ReducedMotion {
    /** A crossfade when the system asks for less motion. */
    System,

    /** Always a crossfade. */
    Always,

    /** Always the wave, where the platform can draw it. */
    Never,
}

/** Which way a distortion wave travels. */
public enum class DistortionWaveType {
    /** Bottom to top, from a wide source such as a button. Leans with the touch. */
    Vertical,

    /** Left to right, from a tall source such as an edge or a side tab. */
    Horizontal,

    /** A ring spreading from the centre of a round source, which stays sharp. */
    Center,
}

/**
 * How a distortion wave looks and moves. Times are in seconds at [speed] 1. Lengths are in dp,
 * multiplied by [scale]. The defaults are tuned for a full phone screen. Start from a preset and
 * change what you need with [copy].
 */
@Immutable
public class DistortionWaveStyle(
    /** Which way the wave travels. */
    public val type: DistortionWaveType = DistortionWaveType.Vertical,
    /**
     * Multiplies every length, for a wave that fills something much smaller or larger than a phone
     * screen.
     */
    public val scale: Float = 1f,
    /**
     * How fast the wave plays: 2 is twice as quick, 0.5 half as quick. The times below are for
     * speed 1.
     */
    public val speed: Float = 1f,
    /** Seconds everything is pulled toward the source before the wave leaves it. */
    public val anticipation: Float = 0.5f,
    /** Seconds from the wave leaving the source to its front having settled. */
    public val travel: Float = 2.28f,
    /** How far out from the source's outline the front starts. */
    public val launchGap: Float = 0f,
    /** Seconds after the launch that the colour on the edges starts to be wiped away. */
    public val linger: Float = -0.35f,
    /** Seconds the colour on the edges takes to be wiped away. */
    public val rimFade: Float = 3.03f,
    /** How the colour on the edges is wiped away over [rimFade]. */
    public val rimEasing: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
    /** How much faster the wave collapses back than it opened. */
    public val returnSpeed: Float = 1.45f,
    /** How the front covers its way over [travel]: full speed off the source, a soft landing. */
    public val easing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    /** How far the anticipation pulls content toward the source. */
    public val pull: Float = 24f,
    /** How far out the ring of colour that falls onto the source starts. */
    public val inhale: Float = 56f,
    /** How strong the colour is through the anticipation, 0–2. */
    public val gatherGlow: Float = 0.33f,
    /** How visible the dots hopping toward the source in the anticipation are, 0–1. */
    public val gatherDots: Float = 1f,
    /** How far the swell lifts content as it passes. */
    public val amplitude: Float = 34f,
    /** Width of the swell. */
    public val lensWidth: Float = 150f,
    /** How far red and blue part, as a share of how far the content has moved. */
    public val aberration: Float = 0.78f,
    /** How far the front bends along its length. */
    public val swell: Float = 22f,
    /** Blur on the front. */
    public val blur: Float = 18f,
    /** Distance ahead of the front over which the blur gathers. */
    public val blurAhead: Float = 90f,
    /** Distance behind the front over which the blur clears. */
    public val blurBehind: Float = 240f,
    /** Distance the colour feathers out ahead of the front. */
    public val lead: Float = 130f,
    /** Length of the colour trailing behind the front. */
    public val tail: Float = 300f,
    /** Peak opacity of the colour, 0–1. */
    public val strength: Float = 0.92f,
    /** How far in from the edges the colour reaches. */
    public val rimWidth: Float = 14f,
    /** How unequal the colour's thickness is along the edges, 0–2. */
    public val rimUnevenness: Float = 1f,
    /** Opacity of the colour on the edges, 0–1. 0 leaves none. */
    public val rimStrength: Float = 0.82f,
    /** How softly the colour is wiped away. */
    public val rimSoftness: Float = 343f,
    /** The corner radius of whatever the wave fills, which the colour on the edges follows. */
    public val cornerRadius: Float = 0f,
    /** How alive the colour on the edges is, 0–1. */
    public val rimPulse: Float = 0.6f,
    /** Width of a hairline right on the edge. 0 draws none. */
    public val hairlineWidth: Float = 1.5f,
    /** Opacity of the hairline, 0–1. */
    public val hairlineOpacity: Float = 0.9f,
    /** How far the hairline whitens where the pulse's light runs, 0–1. */
    public val hairlineWhite: Float = 0.95f,
    /** Seconds the hairline holds on after the colour on the edges starts to go. */
    public val hairlineLinger: Float = 0.47f,
    /** Seconds the hairline takes to be wiped away. */
    public val hairlineFade: Float = 3f,
    /**
     * How much the wave leans toward where the source was touched, 0–1. 0 always sends it straight
     * out. A [DistortionWaveType.Center] wave never leans.
     */
    public val orientation: Float = 0.5f,
    /** How many dots the wave carries. 0 draws none. */
    public val particleCount: Int = 64,
    /** Diameter of the largest dot. */
    public val particleSize: Float = 1.8f,
    /** How far from the source the farthest dot is born in the anticipation. */
    public val particleReach: Float = 150f,
    /** How far each dot flies toward the source in one hop. */
    public val particleFlight: Float = 30f,
    /** How close to the source a dot comes before it has faded. */
    public val particleGap: Float = 24f,
    /** Seconds after the launch by which the last dot has faded out. */
    public val particleLife: Float = 2.19f,
    /** How far the dots are thrown, as a share of the front's way, 0–1. */
    public val particleSpread: Float = 1f,
    /** The wave's colours. */
    public val palette: DistortionWavePalette = DistortionWavePalette.Colorful,
) {
    init {
        require(scale > 0f) { "scale must be above zero, was $scale." }
        require(speed > 0f) { "speed must be above zero, was $speed." }
        require(returnSpeed > 0f) { "returnSpeed must be above zero, was $returnSpeed." }
        require(travel > 0f) { "travel must be above zero, was $travel." }
        require(anticipation >= 0f) { "anticipation must not be negative, was $anticipation." }
        require(particleCount >= 0) { "particleCount must not be negative, was $particleCount." }
    }

    /**
     * Seconds the wave takes at speed 1. The wave is drawn on this timeline, whatever the speed.
     */
    internal val timeline: Float
        get() =
            anticipation +
                maxOf(
                    travel * 0.6f,
                    linger + rimFade,
                    linger + hairlineLinger + hairlineFade,
                    particleLife,
                )

    /** Seconds for the whole of it at [speed]: swell settled, edges cleared, last dot faded. */
    public val duration: Float
        get() = timeline / speed

    /** A copy with the given values changed. */
    public fun copy(
        type: DistortionWaveType = this.type,
        scale: Float = this.scale,
        speed: Float = this.speed,
        anticipation: Float = this.anticipation,
        travel: Float = this.travel,
        launchGap: Float = this.launchGap,
        linger: Float = this.linger,
        rimFade: Float = this.rimFade,
        rimEasing: Easing = this.rimEasing,
        returnSpeed: Float = this.returnSpeed,
        easing: Easing = this.easing,
        pull: Float = this.pull,
        inhale: Float = this.inhale,
        gatherGlow: Float = this.gatherGlow,
        gatherDots: Float = this.gatherDots,
        amplitude: Float = this.amplitude,
        lensWidth: Float = this.lensWidth,
        aberration: Float = this.aberration,
        swell: Float = this.swell,
        blur: Float = this.blur,
        blurAhead: Float = this.blurAhead,
        blurBehind: Float = this.blurBehind,
        lead: Float = this.lead,
        tail: Float = this.tail,
        strength: Float = this.strength,
        rimWidth: Float = this.rimWidth,
        rimUnevenness: Float = this.rimUnevenness,
        rimStrength: Float = this.rimStrength,
        rimSoftness: Float = this.rimSoftness,
        cornerRadius: Float = this.cornerRadius,
        rimPulse: Float = this.rimPulse,
        hairlineWidth: Float = this.hairlineWidth,
        hairlineOpacity: Float = this.hairlineOpacity,
        hairlineWhite: Float = this.hairlineWhite,
        hairlineLinger: Float = this.hairlineLinger,
        hairlineFade: Float = this.hairlineFade,
        orientation: Float = this.orientation,
        particleCount: Int = this.particleCount,
        particleSize: Float = this.particleSize,
        particleReach: Float = this.particleReach,
        particleFlight: Float = this.particleFlight,
        particleGap: Float = this.particleGap,
        particleLife: Float = this.particleLife,
        particleSpread: Float = this.particleSpread,
        palette: DistortionWavePalette = this.palette,
    ): DistortionWaveStyle =
        DistortionWaveStyle(
            type = type,
            scale = scale,
            speed = speed,
            anticipation = anticipation,
            travel = travel,
            launchGap = launchGap,
            linger = linger,
            rimFade = rimFade,
            rimEasing = rimEasing,
            returnSpeed = returnSpeed,
            easing = easing,
            pull = pull,
            inhale = inhale,
            gatherGlow = gatherGlow,
            gatherDots = gatherDots,
            amplitude = amplitude,
            lensWidth = lensWidth,
            aberration = aberration,
            swell = swell,
            blur = blur,
            blurAhead = blurAhead,
            blurBehind = blurBehind,
            lead = lead,
            tail = tail,
            strength = strength,
            rimWidth = rimWidth,
            rimUnevenness = rimUnevenness,
            rimStrength = rimStrength,
            rimSoftness = rimSoftness,
            cornerRadius = cornerRadius,
            rimPulse = rimPulse,
            hairlineWidth = hairlineWidth,
            hairlineOpacity = hairlineOpacity,
            hairlineWhite = hairlineWhite,
            hairlineLinger = hairlineLinger,
            hairlineFade = hairlineFade,
            orientation = orientation,
            particleCount = particleCount,
            particleSize = particleSize,
            particleReach = particleReach,
            particleFlight = particleFlight,
            particleGap = particleGap,
            particleLife = particleLife,
            particleSpread = particleSpread,
            palette = palette,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistortionWaveStyle) return false
        return type == other.type &&
            scale == other.scale &&
            speed == other.speed &&
            anticipation == other.anticipation &&
            travel == other.travel &&
            launchGap == other.launchGap &&
            linger == other.linger &&
            rimFade == other.rimFade &&
            rimEasing == other.rimEasing &&
            returnSpeed == other.returnSpeed &&
            easing == other.easing &&
            pull == other.pull &&
            inhale == other.inhale &&
            gatherGlow == other.gatherGlow &&
            gatherDots == other.gatherDots &&
            amplitude == other.amplitude &&
            lensWidth == other.lensWidth &&
            aberration == other.aberration &&
            swell == other.swell &&
            blur == other.blur &&
            blurAhead == other.blurAhead &&
            blurBehind == other.blurBehind &&
            lead == other.lead &&
            tail == other.tail &&
            strength == other.strength &&
            rimWidth == other.rimWidth &&
            rimUnevenness == other.rimUnevenness &&
            rimStrength == other.rimStrength &&
            rimSoftness == other.rimSoftness &&
            cornerRadius == other.cornerRadius &&
            rimPulse == other.rimPulse &&
            hairlineWidth == other.hairlineWidth &&
            hairlineOpacity == other.hairlineOpacity &&
            hairlineWhite == other.hairlineWhite &&
            hairlineLinger == other.hairlineLinger &&
            hairlineFade == other.hairlineFade &&
            orientation == other.orientation &&
            particleCount == other.particleCount &&
            particleSize == other.particleSize &&
            particleReach == other.particleReach &&
            particleFlight == other.particleFlight &&
            particleGap == other.particleGap &&
            particleLife == other.particleLife &&
            particleSpread == other.particleSpread &&
            palette == other.palette
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + anticipation.hashCode()
        result = 31 * result + travel.hashCode()
        result = 31 * result + launchGap.hashCode()
        result = 31 * result + linger.hashCode()
        result = 31 * result + rimFade.hashCode()
        result = 31 * result + rimEasing.hashCode()
        result = 31 * result + returnSpeed.hashCode()
        result = 31 * result + easing.hashCode()
        result = 31 * result + pull.hashCode()
        result = 31 * result + inhale.hashCode()
        result = 31 * result + gatherGlow.hashCode()
        result = 31 * result + gatherDots.hashCode()
        result = 31 * result + amplitude.hashCode()
        result = 31 * result + lensWidth.hashCode()
        result = 31 * result + aberration.hashCode()
        result = 31 * result + swell.hashCode()
        result = 31 * result + blur.hashCode()
        result = 31 * result + blurAhead.hashCode()
        result = 31 * result + blurBehind.hashCode()
        result = 31 * result + lead.hashCode()
        result = 31 * result + tail.hashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + rimWidth.hashCode()
        result = 31 * result + rimUnevenness.hashCode()
        result = 31 * result + rimStrength.hashCode()
        result = 31 * result + rimSoftness.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        result = 31 * result + rimPulse.hashCode()
        result = 31 * result + hairlineWidth.hashCode()
        result = 31 * result + hairlineOpacity.hashCode()
        result = 31 * result + hairlineWhite.hashCode()
        result = 31 * result + hairlineLinger.hashCode()
        result = 31 * result + hairlineFade.hashCode()
        result = 31 * result + orientation.hashCode()
        result = 31 * result + particleCount.hashCode()
        result = 31 * result + particleSize.hashCode()
        result = 31 * result + particleReach.hashCode()
        result = 31 * result + particleFlight.hashCode()
        result = 31 * result + particleGap.hashCode()
        result = 31 * result + particleLife.hashCode()
        result = 31 * result + particleSpread.hashCode()
        result = 31 * result + palette.hashCode()
        return result
    }

    override fun toString(): String =
        "DistortionWaveStyle(" +
            "type=$type, " +
            "scale=$scale, " +
            "speed=$speed, " +
            "anticipation=$anticipation, " +
            "travel=$travel, " +
            "launchGap=$launchGap, " +
            "linger=$linger, " +
            "rimFade=$rimFade, " +
            "rimEasing=$rimEasing, " +
            "returnSpeed=$returnSpeed, " +
            "easing=$easing, " +
            "pull=$pull, " +
            "inhale=$inhale, " +
            "gatherGlow=$gatherGlow, " +
            "gatherDots=$gatherDots, " +
            "amplitude=$amplitude, " +
            "lensWidth=$lensWidth, " +
            "aberration=$aberration, " +
            "swell=$swell, " +
            "blur=$blur, " +
            "blurAhead=$blurAhead, " +
            "blurBehind=$blurBehind, " +
            "lead=$lead, " +
            "tail=$tail, " +
            "strength=$strength, " +
            "rimWidth=$rimWidth, " +
            "rimUnevenness=$rimUnevenness, " +
            "rimStrength=$rimStrength, " +
            "rimSoftness=$rimSoftness, " +
            "cornerRadius=$cornerRadius, " +
            "rimPulse=$rimPulse, " +
            "hairlineWidth=$hairlineWidth, " +
            "hairlineOpacity=$hairlineOpacity, " +
            "hairlineWhite=$hairlineWhite, " +
            "hairlineLinger=$hairlineLinger, " +
            "hairlineFade=$hairlineFade, " +
            "orientation=$orientation, " +
            "particleCount=$particleCount, " +
            "particleSize=$particleSize, " +
            "particleReach=$particleReach, " +
            "particleFlight=$particleFlight, " +
            "particleGap=$particleGap, " +
            "particleLife=$particleLife, " +
            "particleSpread=$particleSpread, " +
            "palette=$palette" +
            ")"

    public companion object {
        /** Up the screen from a button at the bottom. */
        public val Vertical: DistortionWaveStyle = DistortionWaveStyle()

        /** Across the screen from a tall source at the left. */
        public val Horizontal: DistortionWaveStyle =
            DistortionWaveStyle(type = DistortionWaveType.Horizontal)

        /**
         * Out from an avatar or icon, round a sheet or card about 500dp tall: a slower ring in
         * greens, softer colour, no chromatic aberration.
         */
        public val Center: DistortionWaveStyle =
            DistortionWaveStyle(
                type = DistortionWaveType.Center,
                anticipation = 0.25f,
                travel = 3.43f,
                rimFade = 2.51f,
                hairlineLinger = 0.96f,
                strength = 0.6f,
                rimStrength = 0.6f,
                aberration = 0f,
                gatherGlow = 0f,
                gatherDots = 0f,
                particleCount = 89,
                particleLife = 3.93f,
                particleSpread = 0.64f,
                palette = DistortionWavePalette.Green,
                cornerRadius = 48f,
                pull = 14.4f,
                inhale = 33.6f,
                amplitude = 20.4f,
                lensWidth = 90f,
                swell = 13.2f,
                blur = 10.8f,
                blurAhead = 54f,
                blurBehind = 144f,
                lead = 78f,
                tail = 180f,
                rimSoftness = 205.8f,
                particleReach = 90f,
                particleFlight = 18f,
                particleGap = 11f,
            )
    }
}

/**
 * The colours of a wave: up to eight flowing along the front and round the edges, a fringe riding
 * ahead of the front and one trailing behind it. Use a preset, [single] for one colour of your own,
 * or pass your own colours.
 *
 * @param colors one to [MAX_COLORS] colours, in the order they follow each other along the front.
 * @param ahead the fringe riding ahead of the front.
 * @param behind the fringe trailing behind it.
 */
@Immutable
public class DistortionWavePalette(
    colors: List<Color>,
    public val ahead: Color,
    public val behind: Color,
) {
    /** The colours along the front and round the edges. */
    public val colors: List<Color> = colors.toList()

    init {
        require(colors.size in 1..MAX_COLORS) {
            "A palette takes 1 to $MAX_COLORS colours, got ${colors.size}."
        }
    }

    /** A copy with the given colours changed. */
    public fun copy(
        colors: List<Color> = this.colors,
        ahead: Color = this.ahead,
        behind: Color = this.behind,
    ): DistortionWavePalette = DistortionWavePalette(colors, ahead, behind)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistortionWavePalette) return false
        return colors == other.colors && ahead == other.ahead && behind == other.behind
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + ahead.hashCode()
        result = 31 * result + behind.hashCode()
        return result
    }

    override fun toString(): String =
        "DistortionWavePalette(colors=$colors, ahead=$ahead, behind=$behind)"

    /** OKLab triplets for the band shader, padded to [MAX_COLORS]. */
    internal val packed: FloatArray by lazy {
        FloatArray(MAX_COLORS * 3).also { out ->
            colors.forEachIndexed { i, color -> color.oklab().copyInto(out, i * 3) }
        }
    }
    internal val aheadLab: FloatArray by lazy { ahead.oklab() }
    internal val behindLab: FloatArray by lazy { behind.oklab() }

    public companion object {
        /** The most colours a palette takes. */
        public const val MAX_COLORS: Int = 8

        private fun rgb(
            r: Int,
            g: Int,
            b: Int,
        ) = Color(r, g, b)

        /** Yellow, blue, purple and pink. The default. */
        public val Colorful: DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        rgb(255, 201, 21),
                        rgb(126, 196, 255),
                        rgb(180, 40, 230),
                        rgb(235, 100, 160),
                        rgb(255, 176, 122),
                        rgb(154, 160, 255),
                        rgb(127, 217, 238),
                    ),
                ahead = rgb(255, 122, 182),
                behind = rgb(90, 190, 255),
            )

        /** Greens, for a success. The [DistortionWaveStyle.Center] preset uses it. */
        public val Green: DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        rgb(53, 186, 0),
                        rgb(140, 235, 90),
                        rgb(0, 196, 110),
                        rgb(190, 250, 130),
                        rgb(40, 160, 60),
                        rgb(110, 225, 160),
                        rgb(90, 210, 40),
                    ),
                ahead = rgb(196, 255, 120),
                behind = rgb(0, 150, 70),
            )

        /** Blues and cyans. */
        public val Ocean: DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        rgb(40, 120, 255),
                        rgb(90, 200, 255),
                        rgb(20, 80, 200),
                        rgb(120, 230, 240),
                        rgb(60, 150, 255),
                        rgb(150, 180, 255),
                        rgb(0, 180, 220),
                    ),
                ahead = rgb(150, 230, 255),
                behind = rgb(30, 70, 220),
            )

        /** Oranges, reds and a little magenta. */
        public val Sunset: DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        rgb(255, 140, 60),
                        rgb(255, 90, 120),
                        rgb(255, 200, 80),
                        rgb(240, 70, 90),
                        rgb(255, 170, 120),
                        rgb(220, 90, 200),
                        rgb(255, 120, 80),
                    ),
                ahead = rgb(255, 220, 120),
                behind = rgb(200, 60, 160),
            )

        /** Whites and greys. */
        public val Mono: DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        rgb(240, 240, 245),
                        rgb(200, 205, 215),
                        rgb(255, 255, 255),
                        rgb(170, 175, 190),
                        rgb(225, 228, 235),
                        rgb(190, 195, 205),
                        rgb(245, 245, 250),
                    ),
                ahead = rgb(255, 255, 255),
                behind = rgb(160, 165, 180),
            )

        /** A single colour, lighter ahead of the front and deeper behind it. */
        public fun single(color: Color): DistortionWavePalette =
            DistortionWavePalette(
                colors =
                    listOf(
                        color,
                        lerp(color, Color.White, 0.25f),
                        color,
                        lerp(color, Color.Black, 0.15f),
                    ),
                ahead = lerp(color, Color.White, 0.45f),
                behind = lerp(color, Color.Black, 0.3f),
            )
    }
}

private fun Color.oklab(): FloatArray {
    fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val lr = linear(red)
    val lg = linear(green)
    val lb = linear(blue)
    val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
    val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
    val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
    return floatArrayOf(
        (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat(),
        (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat(),
        (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat(),
    )
}

/** The same wave without the colour it leaves on the edges, and without their hairline. */
public fun DistortionWaveStyle.withoutRim(): DistortionWaveStyle =
    copy(rimStrength = 0f, hairlineWidth = 0f)

/** The same wave without its dots. */
public fun DistortionWaveStyle.withoutParticles(): DistortionWaveStyle = copy(particleCount = 0)

/** The same wave without colour: only the swell, the blur and the dots. */
public fun DistortionWaveStyle.withoutColor(): DistortionWaveStyle =
    copy(strength = 0f, rimStrength = 0f, hairlineWidth = 0f, gatherGlow = 0f)
