package at.florianschuster.distortionwaves

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cbrt
import kotlin.math.pow

/** How much a distortion wave moves. */
public enum class DistortionWaveMotion {
    /** The wave, or a plain crossfade when the system asks for less motion. */
    System,

    /** Always a plain crossfade. */
    Reduced,

    /** Always the wave, where the platform can draw it. */
    Full,
}

/** Which way a distortion wave travels. */
public enum class DistortionWaveType {
    /** Bottom to top, from a wide source such as a button. Leans with the touch. */
    Vertical,

    /**
     * From the start edge across, from a tall source such as an edge or a side tab: left to right,
     * or right to left in a right-to-left layout. Leans with the touch.
     */
    Horizontal,

    /** A ring spreading from the centre of a round source, which stays sharp. */
    Center,
}

/**
 * How a distortion wave looks and moves. Start from a preset and change what you need with [copy].
 * The parts of the wave each have their own group: [anticipation], [lens], [blur], [band], [rim],
 * [hairline] and [particles].
 *
 * Times are in seconds at [speed] 1. Lengths are plain numbers in dp, multiplied by [scale]. The
 * defaults are tuned for a full phone screen.
 *
 * ```
 * val preset = DistortionWaveStyle.Vertical
 * val style = preset.copy(speed = 1.5f, lens = preset.lens.copy(aberration = 0f))
 * ```
 */
@Immutable
public class DistortionWaveStyle(
    /** Which way the wave travels. */
    public val type: DistortionWaveType = DistortionWaveType.Vertical,
    /**
     * Multiplies every length of the wave, for one that fills something much smaller or larger than
     * a phone screen. [cornerRadius] is not scaled.
     */
    public val scale: Float = 1f,
    /**
     * How fast the wave plays: 2 is twice as quick, 0.5 half as quick. The times are for speed 1.
     */
    public val speed: Float = 1f,
    /** How much faster the wave collapses back than it opened. */
    public val returnSpeed: Float = 1.45f,
    /** Seconds from the wave leaving the source to its front having settled. */
    public val travel: Float = 2.28f,
    /** How the front covers its way over [travel]: full speed off the source, a soft landing. */
    public val travelEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    /** How far out from the source's outline the front starts. */
    public val launchGap: Float = 0f,
    /**
     * How much the wave leans toward where the source was touched, 0–1. 0 always sends it straight
     * out. A [DistortionWaveType.Center] wave never leans.
     */
    public val lean: Float = 0.5f,
    /**
     * The corner radius of whatever the wave fills, which the colour on the edges follows. In plain
     * dp, whatever the [scale].
     */
    public val cornerRadius: Dp = 0.dp,
    /** The wind-up before the wave leaves. */
    public val anticipation: Anticipation = Anticipation(),
    /** The distortion on the front. */
    public val lens: Lens = Lens(),
    /** The blur on the front. */
    public val blur: Blur = Blur(),
    /** The colour riding on the front. */
    public val band: Band = Band(),
    /** The colour left on the edges. */
    public val rim: Rim = Rim(),
    /** The thin line right on the edge. */
    public val hairline: Hairline = Hairline(),
    /** The dots the wave carries. */
    public val particles: Particles = Particles(),
    /** The wave's colours. */
    public val palette: DistortionWavePalette = DistortionWavePalette.Colorful,
) {
    init {
        require(scale > 0f) { "scale must be above zero, was $scale." }
        require(speed > 0f) { "speed must be above zero, was $speed." }
        require(returnSpeed > 0f) { "returnSpeed must be above zero, was $returnSpeed." }
        require(travel > 0f) { "travel must be above zero, was $travel." }
        require(cornerRadius >= 0.dp) { "cornerRadius must not be negative, was $cornerRadius." }
    }

    /**
     * Seconds the wave takes at speed 1. The wave is drawn on this timeline, whatever the speed.
     */
    internal val timeline: Float
        get() =
            anticipation.duration +
                maxOf(
                    travel * 0.6f,
                    rim.delay + rim.fade,
                    rim.delay + hairline.delay + hairline.fade,
                    particles.life,
                )

    /** Seconds for the whole of it at [speed]: front settled, edges cleared, last dot faded. */
    public val duration: Float
        get() = timeline / speed

    /** [cornerRadius] in the wave's own points, which the shaders work in. */
    internal val cornerRadiusPoints: Float
        get() = cornerRadius.value / scale

    /** A copy with the given values changed. */
    public fun copy(
        type: DistortionWaveType = this.type,
        scale: Float = this.scale,
        speed: Float = this.speed,
        returnSpeed: Float = this.returnSpeed,
        travel: Float = this.travel,
        travelEasing: Easing = this.travelEasing,
        launchGap: Float = this.launchGap,
        lean: Float = this.lean,
        cornerRadius: Dp = this.cornerRadius,
        anticipation: Anticipation = this.anticipation,
        lens: Lens = this.lens,
        blur: Blur = this.blur,
        band: Band = this.band,
        rim: Rim = this.rim,
        hairline: Hairline = this.hairline,
        particles: Particles = this.particles,
        palette: DistortionWavePalette = this.palette,
    ): DistortionWaveStyle =
        DistortionWaveStyle(
            type = type,
            scale = scale,
            speed = speed,
            returnSpeed = returnSpeed,
            travel = travel,
            travelEasing = travelEasing,
            launchGap = launchGap,
            lean = lean,
            cornerRadius = cornerRadius,
            anticipation = anticipation,
            lens = lens,
            blur = blur,
            band = band,
            rim = rim,
            hairline = hairline,
            particles = particles,
            palette = palette,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DistortionWaveStyle) return false
        return type == other.type &&
            scale == other.scale &&
            speed == other.speed &&
            returnSpeed == other.returnSpeed &&
            travel == other.travel &&
            travelEasing == other.travelEasing &&
            launchGap == other.launchGap &&
            lean == other.lean &&
            cornerRadius == other.cornerRadius &&
            anticipation == other.anticipation &&
            lens == other.lens &&
            blur == other.blur &&
            band == other.band &&
            rim == other.rim &&
            hairline == other.hairline &&
            particles == other.particles &&
            palette == other.palette
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + returnSpeed.hashCode()
        result = 31 * result + travel.hashCode()
        result = 31 * result + travelEasing.hashCode()
        result = 31 * result + launchGap.hashCode()
        result = 31 * result + lean.hashCode()
        result = 31 * result + cornerRadius.hashCode()
        result = 31 * result + anticipation.hashCode()
        result = 31 * result + lens.hashCode()
        result = 31 * result + blur.hashCode()
        result = 31 * result + band.hashCode()
        result = 31 * result + rim.hashCode()
        result = 31 * result + hairline.hashCode()
        result = 31 * result + particles.hashCode()
        result = 31 * result + palette.hashCode()
        return result
    }

    override fun toString(): String =
        "DistortionWaveStyle(" +
            "type=$type" +
            ", " +
            "scale=$scale" +
            ", " +
            "speed=$speed" +
            ", " +
            "returnSpeed=$returnSpeed" +
            ", " +
            "travel=$travel" +
            ", " +
            "travelEasing=$travelEasing" +
            ", " +
            "launchGap=$launchGap" +
            ", " +
            "lean=$lean" +
            ", " +
            "cornerRadius=$cornerRadius" +
            ", " +
            "anticipation=$anticipation" +
            ", " +
            "lens=$lens" +
            ", " +
            "blur=$blur" +
            ", " +
            "band=$band" +
            ", " +
            "rim=$rim" +
            ", " +
            "hairline=$hairline" +
            ", " +
            "particles=$particles" +
            ", " +
            "palette=$palette" +
            ")"

    /** The wind-up before the wave leaves: everything is pulled toward the source. */
    @Immutable
    public class Anticipation(
        /**
         * Seconds everything is pulled toward the source before the wave leaves it. 0 skips the
         * wind-up.
         */
        public val duration: Float = 0.5f,
        /** How far content is pulled toward the source. */
        public val pull: Float = 24f,
        /** How far out the ring of colour that falls onto the source starts. */
        public val inhale: Float = 56f,
        /** How strong the colour is through the wind-up, 0–2. */
        public val glow: Float = 0.33f,
        /** How visible the dots hopping toward the source are, 0–1. */
        public val dots: Float = 1f,
    ) {
        init {
            require(duration >= 0f) { "duration must not be negative, was $duration." }
        }

        /** A copy with the given values changed. */
        public fun copy(
            duration: Float = this.duration,
            pull: Float = this.pull,
            inhale: Float = this.inhale,
            glow: Float = this.glow,
            dots: Float = this.dots,
        ): Anticipation =
            Anticipation(
                duration = duration,
                pull = pull,
                inhale = inhale,
                glow = glow,
                dots = dots,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Anticipation) return false
            return duration == other.duration &&
                pull == other.pull &&
                inhale == other.inhale &&
                glow == other.glow &&
                dots == other.dots
        }

        override fun hashCode(): Int {
            var result = duration.hashCode()
            result = 31 * result + pull.hashCode()
            result = 31 * result + inhale.hashCode()
            result = 31 * result + glow.hashCode()
            result = 31 * result + dots.hashCode()
            return result
        }

        override fun toString(): String =
            "Anticipation(" +
                "duration=$duration" +
                ", " +
                "pull=$pull" +
                ", " +
                "inhale=$inhale" +
                ", " +
                "glow=$glow" +
                ", " +
                "dots=$dots" +
                ")"
    }

    /** The distortion on the front: content lifts once as the front passes and settles back. */
    @Immutable
    public class Lens(
        /** How far the lens lifts content as it passes. */
        public val amplitude: Float = 34f,
        /** Width of the lens across the front. */
        public val width: Float = 150f,
        /**
         * How far red and blue part, as a share of how far the content has moved. 0 keeps colours
         * together.
         */
        public val aberration: Float = 0.78f,
        /** How far the front wobbles from a clean arc along its length. */
        public val bend: Float = 22f,
    ) {
        /** A copy with the given values changed. */
        public fun copy(
            amplitude: Float = this.amplitude,
            width: Float = this.width,
            aberration: Float = this.aberration,
            bend: Float = this.bend,
        ): Lens =
            Lens(
                amplitude = amplitude,
                width = width,
                aberration = aberration,
                bend = bend,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Lens) return false
            return amplitude == other.amplitude &&
                width == other.width &&
                aberration == other.aberration &&
                bend == other.bend
        }

        override fun hashCode(): Int {
            var result = amplitude.hashCode()
            result = 31 * result + width.hashCode()
            result = 31 * result + aberration.hashCode()
            result = 31 * result + bend.hashCode()
            return result
        }

        override fun toString(): String =
            "Lens(" +
                "amplitude=$amplitude" +
                ", " +
                "width=$width" +
                ", " +
                "aberration=$aberration" +
                ", " +
                "bend=$bend" +
                ")"
    }

    /** The blur that peaks on the front. */
    @Immutable
    public class Blur(
        /** The most blur, right on the front. 0 keeps everything sharp. */
        public val radius: Float = 18f,
        /** Distance ahead of the front over which the blur gathers. */
        public val ahead: Float = 90f,
        /** Distance behind the front over which the blur clears. */
        public val behind: Float = 240f,
    ) {
        /** A copy with the given values changed. */
        public fun copy(
            radius: Float = this.radius,
            ahead: Float = this.ahead,
            behind: Float = this.behind,
        ): Blur =
            Blur(
                radius = radius,
                ahead = ahead,
                behind = behind,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Blur) return false
            return radius == other.radius && ahead == other.ahead && behind == other.behind
        }

        override fun hashCode(): Int {
            var result = radius.hashCode()
            result = 31 * result + ahead.hashCode()
            result = 31 * result + behind.hashCode()
            return result
        }

        override fun toString(): String =
            "Blur(" + "radius=$radius" + ", " + "ahead=$ahead" + ", " + "behind=$behind" + ")"
    }

    /** The colour riding on the front. */
    @Immutable
    public class Band(
        /** Distance the colour feathers out ahead of the front. */
        public val lead: Float = 130f,
        /** Length of the colour trailing behind the front. */
        public val tail: Float = 300f,
        /** Peak opacity of the colour, 0–1. 0 draws none. */
        public val opacity: Float = 0.92f,
    ) {
        /** A copy with the given values changed. */
        public fun copy(
            lead: Float = this.lead,
            tail: Float = this.tail,
            opacity: Float = this.opacity,
        ): Band =
            Band(
                lead = lead,
                tail = tail,
                opacity = opacity,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Band) return false
            return lead == other.lead && tail == other.tail && opacity == other.opacity
        }

        override fun hashCode(): Int {
            var result = lead.hashCode()
            result = 31 * result + tail.hashCode()
            result = 31 * result + opacity.hashCode()
            return result
        }

        override fun toString(): String =
            "Band(" + "lead=$lead" + ", " + "tail=$tail" + ", " + "opacity=$opacity" + ")"
    }

    /** The colour the wave leaves on the edges, until a second, later front wipes it away. */
    @Immutable
    public class Rim(
        /** How far in from the edges the colour reaches. */
        public val width: Float = 14f,
        /** Opacity of the colour on the edges, 0–1. 0 leaves none. */
        public val opacity: Float = 0.82f,
        /** How unequal the colour's thickness is along the edges, 0–2. */
        public val unevenness: Float = 1f,
        /** How softly the colour is wiped away. */
        public val softness: Float = 343f,
        /** How alive the colour on the edges is, 0–1. */
        public val pulse: Float = 0.6f,
        /**
         * Seconds from the launch until the wipe starts. Below zero, the wipe is already that far
         * along at the launch, so the colour goes sooner.
         */
        public val delay: Float = -0.35f,
        /** Seconds the wipe takes. */
        public val fade: Float = 3.03f,
        /** How the wipe covers its way over [fade]. */
        public val easing: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f),
    ) {
        /** A copy with the given values changed. */
        public fun copy(
            width: Float = this.width,
            opacity: Float = this.opacity,
            unevenness: Float = this.unevenness,
            softness: Float = this.softness,
            pulse: Float = this.pulse,
            delay: Float = this.delay,
            fade: Float = this.fade,
            easing: Easing = this.easing,
        ): Rim =
            Rim(
                width = width,
                opacity = opacity,
                unevenness = unevenness,
                softness = softness,
                pulse = pulse,
                delay = delay,
                fade = fade,
                easing = easing,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Rim) return false
            return width == other.width &&
                opacity == other.opacity &&
                unevenness == other.unevenness &&
                softness == other.softness &&
                pulse == other.pulse &&
                delay == other.delay &&
                fade == other.fade &&
                easing == other.easing
        }

        override fun hashCode(): Int {
            var result = width.hashCode()
            result = 31 * result + opacity.hashCode()
            result = 31 * result + unevenness.hashCode()
            result = 31 * result + softness.hashCode()
            result = 31 * result + pulse.hashCode()
            result = 31 * result + delay.hashCode()
            result = 31 * result + fade.hashCode()
            result = 31 * result + easing.hashCode()
            return result
        }

        override fun toString(): String =
            "Rim(" +
                "width=$width" +
                ", " +
                "opacity=$opacity" +
                ", " +
                "unevenness=$unevenness" +
                ", " +
                "softness=$softness" +
                ", " +
                "pulse=$pulse" +
                ", " +
                "delay=$delay" +
                ", " +
                "fade=$fade" +
                ", " +
                "easing=$easing" +
                ")"
    }

    /** A thin bright line right on the edge, wiped away after the [Rim]. */
    @Immutable
    public class Hairline(
        /** Width of the line. 0 draws none. */
        public val width: Float = 1.5f,
        /** Opacity of the line, 0–1. */
        public val opacity: Float = 0.9f,
        /** How far the line whitens where the pulse's light runs, 0–1. */
        public val white: Float = 0.95f,
        /** Seconds the line holds on after the rim's wipe has started. */
        public val delay: Float = 0.47f,
        /** Seconds the line takes to be wiped away. */
        public val fade: Float = 3f,
    ) {
        /** A copy with the given values changed. */
        public fun copy(
            width: Float = this.width,
            opacity: Float = this.opacity,
            white: Float = this.white,
            delay: Float = this.delay,
            fade: Float = this.fade,
        ): Hairline =
            Hairline(
                width = width,
                opacity = opacity,
                white = white,
                delay = delay,
                fade = fade,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Hairline) return false
            return width == other.width &&
                opacity == other.opacity &&
                white == other.white &&
                delay == other.delay &&
                fade == other.fade
        }

        override fun hashCode(): Int {
            var result = width.hashCode()
            result = 31 * result + opacity.hashCode()
            result = 31 * result + white.hashCode()
            result = 31 * result + delay.hashCode()
            result = 31 * result + fade.hashCode()
            return result
        }

        override fun toString(): String =
            "Hairline(" +
                "width=$width" +
                ", " +
                "opacity=$opacity" +
                ", " +
                "white=$white" +
                ", " +
                "delay=$delay" +
                ", " +
                "fade=$fade" +
                ")"
    }

    /** The tiny white dots the wave carries. */
    @Immutable
    public class Particles(
        /** How many dots. 0 draws none. */
        public val count: Int = 64,
        /** Diameter of the largest dot. */
        public val size: Float = 1.8f,
        /** How far from the source the farthest dot is born in the wind-up. */
        public val reach: Float = 150f,
        /** How far each dot flies toward the source in one hop. */
        public val flight: Float = 30f,
        /** How close to the source a dot comes before it has faded. */
        public val gap: Float = 24f,
        /** Seconds after the launch by which the last dot has faded out. */
        public val life: Float = 2.19f,
        /** How far the dots are thrown, as a share of the front's way, 0–1. */
        public val spread: Float = 1f,
    ) {
        init {
            require(count >= 0) { "count must not be negative, was $count." }
        }

        /** A copy with the given values changed. */
        public fun copy(
            count: Int = this.count,
            size: Float = this.size,
            reach: Float = this.reach,
            flight: Float = this.flight,
            gap: Float = this.gap,
            life: Float = this.life,
            spread: Float = this.spread,
        ): Particles =
            Particles(
                count = count,
                size = size,
                reach = reach,
                flight = flight,
                gap = gap,
                life = life,
                spread = spread,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Particles) return false
            return count == other.count &&
                size == other.size &&
                reach == other.reach &&
                flight == other.flight &&
                gap == other.gap &&
                life == other.life &&
                spread == other.spread
        }

        override fun hashCode(): Int {
            var result = count.hashCode()
            result = 31 * result + size.hashCode()
            result = 31 * result + reach.hashCode()
            result = 31 * result + flight.hashCode()
            result = 31 * result + gap.hashCode()
            result = 31 * result + life.hashCode()
            result = 31 * result + spread.hashCode()
            return result
        }

        override fun toString(): String =
            "Particles(" +
                "count=$count" +
                ", " +
                "size=$size" +
                ", " +
                "reach=$reach" +
                ", " +
                "flight=$flight" +
                ", " +
                "gap=$gap" +
                ", " +
                "life=$life" +
                ", " +
                "spread=$spread" +
                ")"
    }

    public companion object {
        /** Up the screen from a button at the bottom. */
        public val Vertical: DistortionWaveStyle = DistortionWaveStyle()

        /** Across the screen from a tall source at the start edge. */
        public val Horizontal: DistortionWaveStyle =
            DistortionWaveStyle(type = DistortionWaveType.Horizontal)

        /** A ring out from a round source, with the same look as the other two. */
        public val Center: DistortionWaveStyle =
            DistortionWaveStyle(type = DistortionWaveType.Center)

        /**
         * A success: out from an avatar or icon, round a sheet or card about 500dp tall with 48dp
         * corners. A slower ring in greens, softer colour, no chromatic aberration and no wind-up
         * glow.
         */
        public val Success: DistortionWaveStyle =
            DistortionWaveStyle(
                type = DistortionWaveType.Center,
                travel = 3.43f,
                cornerRadius = 48.dp,
                anticipation =
                    Anticipation(
                        duration = 0.25f,
                        pull = 14.4f,
                        inhale = 33.6f,
                        glow = 0f,
                        dots = 0f,
                    ),
                lens = Lens(amplitude = 20.4f, width = 90f, aberration = 0f, bend = 13.2f),
                blur = Blur(radius = 10.8f, ahead = 54f, behind = 144f),
                band = Band(lead = 78f, tail = 180f, opacity = 0.6f),
                rim = Rim(opacity = 0.6f, softness = 205.8f, fade = 2.51f),
                hairline = Hairline(delay = 0.96f),
                particles =
                    Particles(
                        count = 89,
                        life = 3.93f,
                        spread = 0.64f,
                        reach = 90f,
                        flight = 18f,
                        gap = 11f,
                    ),
                palette = DistortionWavePalette.Green,
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

        /** Greens, for a success. The [DistortionWaveStyle.Success] preset uses it. */
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

        /** The built-in palettes, in the order a picker would show them. */
        public val Presets: List<DistortionWavePalette> =
            listOf(Colorful, Green, Ocean, Sunset, Mono)

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
    copy(rim = rim.copy(opacity = 0f), hairline = hairline.copy(width = 0f))

/** The same wave without its dots. */
public fun DistortionWaveStyle.withoutParticles(): DistortionWaveStyle =
    copy(particles = particles.copy(count = 0))

/** The same wave without colour: only the lens, the blur and the dots. */
public fun DistortionWaveStyle.withoutColor(): DistortionWaveStyle =
    copy(
        band = band.copy(opacity = 0f),
        rim = rim.copy(opacity = 0f),
        hairline = hairline.copy(width = 0f),
        anticipation = anticipation.copy(glow = 0f),
    )
