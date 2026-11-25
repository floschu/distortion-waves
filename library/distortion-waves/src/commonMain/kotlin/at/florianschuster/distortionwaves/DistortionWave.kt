package at.florianschuster.distortionwaves

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import at.florianschuster.distortionwaves.internal.BAND_SHADER
import at.florianschuster.distortionwaves.internal.BLUR_MASK_SHADER
import at.florianschuster.distortionwaves.internal.LENS_SHADER
import at.florianschuster.distortionwaves.internal.WaveFront
import at.florianschuster.distortionwaves.internal.WaveParticle
import at.florianschuster.distortionwaves.internal.WaveRim
import at.florianschuster.distortionwaves.internal.WaveShader
import at.florianschuster.distortionwaves.internal.createWaveShader
import at.florianschuster.distortionwaves.internal.setField
import at.florianschuster.distortionwaves.internal.systemReducedMotion
import at.florianschuster.distortionwaves.internal.waveSideEffect
import at.florianschuster.distortionwaves.internal.wavesSupported

/**
 * A colourful distortion wave that opens from a source (the button, edge or avatar that started it)
 * and carries one view into another.
 *
 * Everything falls toward the source for a moment, tiny sparks with it. Then the front leaves the
 * source and travels outward as one soft swell, carrying the sparks and the palette, with a
 * progressive blur peaking on it. The colour stays on the edges after the middle has cleared.
 *
 * Give both sides an opaque background. Where the platform has no runtime shaders (Android before
 * 13), and when the system asks for less motion, the wave is a crossfade.
 *
 * Both sides stay composed the whole time, so the wave starts without a hitch and each side keeps
 * its state. The side that is not on show is drawn transparent, takes no touches once the wave has
 * passed and is left out of the accessibility tree. Start work that belongs to the revealed side
 * from [onFinish] or from the event that flips [revealed], not from its composition.
 *
 * @param revealed flip it to send the wave out, flip it back to collapse it. A flip mid-wave turns
 *   the wave around from where it is.
 * @param style how the wave looks and moves. Start from [DistortionWaveStyle.Vertical],
 *   [DistortionWaveStyle.Horizontal] or [DistortionWaveStyle.Center].
 * @param source the frame the wave opens from, in this composable's own coordinates, in pixels.
 *   Without one, a point on the edge the wave's type starts from.
 * @param focus where the source was touched, in the same coordinates. The wave leans toward it as
 *   much as the style's `orientation` says.
 * @param reducedMotion whether the wave gives way to a crossfade. By default, when the system asks
 *   for less motion.
 * @param onFinish called when the wave has left and the edges have cleared, or when it has
 *   collapsed back. Not called for a run that was turned back.
 * @param overlay stays above the wave, sharp and live, over both sides: the place for the button
 *   that starts it.
 * @param revealedContent the view the wave leaves behind.
 * @param content the view the wave starts from.
 */
@Composable
public fun DistortionWave(
    revealed: Boolean,
    modifier: Modifier = Modifier,
    style: DistortionWaveStyle = DistortionWaveStyle.Vertical,
    source: Rect? = null,
    focus: Offset? = null,
    reducedMotion: ReducedMotion = ReducedMotion.System,
    onFinish: (revealed: Boolean) -> Unit = {},
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    revealedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    // Not saved: after a restore, the caller's boolean says which side shows.
    val state = remember { DistortionWaveState(initiallyRevealed = revealed) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    val currentFocus by rememberUpdatedState(focus)
    var container by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(revealed) {
        if (state.progress == (if (revealed) 1f else 0f)) return@LaunchedEffect
        if (revealed) state.reveal(currentFocus?.let { it + container }) else state.collapse()
        currentOnFinish(revealed)
    }

    DistortionWave(
        state = state,
        modifier = modifier.onGloballyPositioned { container = it.positionInRoot() },
        style = style,
        source = source,
        reducedMotion = reducedMotion,
        overlay = overlay,
        revealedContent = revealedContent,
        content = content,
    )
}

/**
 * A [DistortionWave] driven by a [DistortionWaveState]: call [DistortionWaveState.reveal],
 * [DistortionWaveState.collapse], [DistortionWaveState.toggle] or [DistortionWaveState.play] from a
 * coroutine. See the overload that takes a boolean for the other parameters.
 *
 * @param state create it with [rememberDistortionWaveState]. One state drives one wave.
 * @param source the frame the wave opens from, in this composable's own coordinates, in pixels.
 *   Without one, the element marked with [distortionWaveSource], and without that either, a point
 *   on the edge the wave's type starts from.
 */
@Composable
public fun DistortionWave(
    state: DistortionWaveState,
    modifier: Modifier = Modifier,
    style: DistortionWaveStyle = DistortionWaveStyle.Vertical,
    source: Rect? = null,
    reducedMotion: ReducedMotion = ReducedMotion.System,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    revealedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val plain =
        !wavesSupported ||
            when (reducedMotion) {
                ReducedMotion.System -> systemReducedMotion()
                ReducedMotion.Always -> true
                ReducedMotion.Never -> false
            }
    SideEffect {
        state.style = style
        state.plain = plain
    }

    // These two only change as the wave starts and ends, so reading them here
    // does not recompose every frame.
    val atStart by remember(state) { derivedStateOf { state.progress <= 0f } }
    val atEnd by remember(state) { derivedStateOf { state.progress >= 1f } }

    // Both sides take on the size this composable is given at least, so a
    // view that wraps its content still fills a wave with a set size.
    Box(modifier, propagateMinConstraints = true) {
        if (plain) {
            Box(
                Modifier.hidden(atStart).graphicsLayer { alpha = state.progress },
                propagateMinConstraints = true,
                content = revealedContent,
            )
            Box(
                Modifier.zIndex(if (atEnd) -1f else 0f).hidden(atEnd).graphicsLayer {
                    alpha = 1f - state.progress
                },
                propagateMinConstraints = true,
                content = content,
            )
        } else {
            WaveStage(state, atStart, atEnd, source, style, revealedContent, content)
        }
        if (overlay != null) Box(Modifier.matchParentSize().zIndex(1f), content = overlay)
    }
}

/** Keeps a side that cannot be seen out of the accessibility tree. */
private fun Modifier.hidden(hidden: Boolean) = if (hidden) clearAndSetSemantics {} else this

/**
 * The transition at one progress. Everything on screen is derived from that one number, and it is
 * read only while drawing: a frame of the wave redraws the layers without recomposing anything.
 */
@Composable
private fun BoxScope.WaveStage(
    state: DistortionWaveState,
    atStart: Boolean,
    atEnd: Boolean,
    source: Rect?,
    style: DistortionWaveStyle,
    revealedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    // Pixels in one of the style's points.
    val unit = LocalDensity.current.density * style.scale
    var size by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val currentSource by rememberUpdatedState(source)
    val currentStyle by rememberUpdatedState(style)
    val currentUnit by rememberUpdatedState(unit)

    val front by
        remember(state) {
            derivedStateOf {
                val u = currentUnit
                val s =
                    currentSource
                        ?: state.sourceInRoot?.translate(-position)
                        ?: defaultSource(currentStyle.type, size)
                WaveFront(
                    progress = state.progress,
                    source = Rect(s.left / u, s.top / u, s.right / u, s.bottom / u),
                    size = Size(size.width / u, size.height / u),
                    style = currentStyle,
                    focus = state.focusInRoot?.let { (it - position) / u },
                )
            }
        }

    val incoming = remember { WaveSide(side = -1f) }
    val outgoing = remember { WaveSide(side = 1f) }
    val band = remember { WaveBand() }
    val particles = remember(style) { List(style.particleCount) { WaveParticle(it, style) } }

    Box(
        Modifier.matchParentSize().onGloballyPositioned {
            size = it.size
            position = it.positionInRoot()
        },
    )
    Box(
        Modifier.hidden(atStart).graphicsLayer {
            val f = front
            val acting = state.progress.let { it > 0f && it < 1f } && f.energy > 0f
            // This side's layer is set up while the wave gathers, when it
            // draws nothing yet. Setting it up as the front leaves costs
            // a frame just when everything starts to move.
            val warming = state.progress > 0f && !f.launched
            alpha = if (f.launched || warming) 1f else 0f
            renderEffect =
                incoming.effect(f, style, unit, lens = acting || warming, blurred = acting)
        },
        propagateMinConstraints = true,
        content = revealedContent,
    )
    Box(
        Modifier
            // Out of the way of touches once the other side is all there is.
            .zIndex(if (atEnd) -1f else 0f)
            .hidden(atEnd)
            .graphicsLayer {
                val f = front
                val live = state.progress.let { it > 0f && it < 1f }
                val acting = live && f.energy > 0f
                // Once the front has crossed, nothing of this side shows.
                alpha = if (f.hasCleared) 0f else 1f
                renderEffect =
                    outgoing.effect(
                        f,
                        style,
                        unit,
                        lens = (acting || (live && f.gravity > 0f)) && !f.hasCleared,
                        blurred = acting && !f.hasCleared,
                    )
            },
        propagateMinConstraints = true,
        content = content,
    )
    Canvas(Modifier.matchParentSize()) {
        val f = front
        if (state.progress.let { it <= 0f || it >= 1f }) return@Canvas
        drawRect(band.brush(f, style, unit))
        // The dots, over the wave's colour: flat white, nothing around them.
        for (particle in particles) {
            val opacity = particle.opacity(f)
            if (opacity <= 0f) continue
            drawCircle(
                Color.White,
                radius = particle.size / 2f * unit,
                center = particle.point(f) * unit,
                alpha = opacity,
            )
        }
    }
}

/** Where a wave with no source opens from: a point on the edge its type starts at. */
private fun defaultSource(
    type: DistortionWaveType,
    size: IntSize,
): Rect {
    val centre =
        when (type) {
            DistortionWaveType.Vertical -> Offset(size.width / 2f, size.height.toFloat())
            DistortionWaveType.Horizontal -> Offset(0f, size.height / 2f)
            DistortionWaveType.Center -> Offset(size.width / 2f, size.height / 2f)
        }
    return Rect(centre, 1f)
}

/** One side of the transition: its lens, and the masks of its two blurred copies. */
private class WaveSide(
    private val side: Float,
) {
    private val lens = createWaveShader(LENS_SHADER)
    private val nearMask = createWaveShader(BLUR_MASK_SHADER)
    private val farMask = createWaveShader(BLUR_MASK_SHADER)

    fun effect(
        front: WaveFront,
        style: DistortionWaveStyle,
        unit: Float,
        lens: Boolean,
        blurred: Boolean,
    ): RenderEffect? {
        if (!lens) return null
        this.lens.apply {
            setField(front, unit)
            set("amplitude", style.amplitude)
            set("lensWidth", style.lensWidth)
            set("aberration", style.aberration)
            set("side", side)
        }
        // The most blur anywhere on screen in this frame.
        val peak = front.energy * style.blur
        if (!blurred || peak < MIN_BLUR) return waveSideEffect(this.lens, null, null, 0f, 0f)
        val near = minOf(NEAR_BLUR, style.blur)
        mask(nearMask, front, style, unit, from = 0f, to = near)
        // The second copy only takes over past the first one's blur.
        if (peak <= near) return waveSideEffect(this.lens, nearMask, null, near * unit, 0f)
        mask(farMask, front, style, unit, from = near, to = style.blur)
        return waveSideEffect(this.lens, nearMask, farMask, near * unit, style.blur * unit)
    }

    private fun mask(
        mask: WaveShader,
        front: WaveFront,
        style: DistortionWaveStyle,
        unit: Float,
        from: Float,
        to: Float,
    ) {
        mask.setField(front, unit)
        mask.set("blurRadius", style.blur)
        mask.set("blurAhead", style.blurAhead)
        mask.set("blurBehind", style.blurBehind)
        mask.set("blurFrom", from)
        mask.set("blurTo", to)
    }

    private companion object {
        /** The blur of the first blurred copy. The second has the full blur. */
        const val NEAR_BLUR = 7f

        /** Less blur than this can't be seen. */
        const val MIN_BLUR = 0.25f
    }
}

/** The wave's colour: the band on the front and what it leaves on the edges. */
private class WaveBand {
    private val shader = createWaveShader(BAND_SHADER)
    private val lumps = FloatArray(21)

    fun brush(
        front: WaveFront,
        style: DistortionWaveStyle,
        unit: Float,
    ) = shader.run {
        val palette = style.palette
        setField(front, unit)
        set("palette", palette.packed)
        set("paletteCount", palette.colors.size.toFloat())
        set("warm", palette.aheadLab)
        set("cool", palette.behindLab)
        set("lead", style.lead)
        set("tail", style.tail)
        set("strength", style.strength)
        set("rimWidth", style.rimWidth)
        set("rimSoftness", style.rimSoftness)
        set("cornerRadius", style.cornerRadius)
        set("unevenness", style.rimUnevenness)
        set("pulse", style.rimPulse)
        set("hairWidth", style.hairlineWidth)
        set("hairOpacity", style.hairlineOpacity)
        set("hairWhite", style.hairlineWhite)
        set(
            "lumps",
            WaveRim.lumps(
                front.time,
                front.width,
                front.height,
                style.cornerRadius,
                style.rimPulse,
                lumps,
            ),
        )
        brush()
    }
}
