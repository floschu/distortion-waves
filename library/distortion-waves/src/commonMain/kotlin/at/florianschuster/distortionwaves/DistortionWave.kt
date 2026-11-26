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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
 * its state. The side that is not on show is not drawn, takes no touches and no keyboard focus, and
 * is left out of the accessibility tree. Start work that belongs to the revealed side from
 * [onFinish] or from the event that flips [revealed], not from its composition.
 *
 * @param revealed flip it to send the wave out, flip it back to collapse it. A flip mid-wave turns
 *   the wave around from where it is.
 * @param style how the wave looks and moves. Start from a preset such as
 *   [DistortionWaveStyle.Vertical].
 * @param source the frame the wave opens from, in this composable's own coordinates, in pixels.
 *   Without one, the element marked with [DistortionWaveScope.distortionWaveSource], and without
 *   that either, a point on the edge the wave's type starts from.
 * @param focus where the source was touched, in the same coordinates. Without one, the last press
 *   on the marked element. The wave leans toward it as much as the style's `lean` says.
 * @param motion whether the wave gives way to a crossfade. By default, when the system asks for
 *   less motion.
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
    motion: DistortionWaveMotion = DistortionWaveMotion.System,
    onFinish: (revealed: Boolean) -> Unit = {},
    overlay: (@Composable DistortionWaveScope.() -> Unit)? = null,
    revealedContent: @Composable DistortionWaveScope.() -> Unit,
    content: @Composable DistortionWaveScope.() -> Unit,
) {
    // Not saved: after a restore, the caller's boolean says which side shows.
    val state = remember { DistortionWaveState(initiallyRevealed = revealed) }
    val currentOnFinish by rememberUpdatedState(onFinish)
    val currentFocus by rememberUpdatedState(focus)

    LaunchedEffect(revealed) {
        if (state.progress == (if (revealed) 1f else 0f)) return@LaunchedEffect
        if (revealed) state.reveal(currentFocus) else state.collapse()
        currentOnFinish(revealed)
    }

    DistortionWave(
        state = state,
        modifier = modifier,
        style = style,
        source = source,
        motion = motion,
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
 *   Without one, the element marked with `distortionWaveSource`, and without that either, a point
 *   on the edge the wave's type starts from.
 */
@Composable
public fun DistortionWave(
    state: DistortionWaveState,
    modifier: Modifier = Modifier,
    style: DistortionWaveStyle = DistortionWaveStyle.Vertical,
    source: Rect? = null,
    motion: DistortionWaveMotion = DistortionWaveMotion.System,
    overlay: (@Composable DistortionWaveScope.() -> Unit)? = null,
    revealedContent: @Composable DistortionWaveScope.() -> Unit,
    content: @Composable DistortionWaveScope.() -> Unit,
) {
    val plain =
        !wavesSupported ||
            when (motion) {
                DistortionWaveMotion.System -> systemReducedMotion()
                DistortionWaveMotion.Reduced -> true
                DistortionWaveMotion.Full -> false
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
    Box(
        modifier.onGloballyPositioned { state.positionInRoot = it.positionInRoot() },
        propagateMinConstraints = true,
    ) {
        if (plain) {
            WaveSlot(
                state,
                Modifier.placedUnless(atStart).graphicsLayer { alpha = state.progress },
                revealedContent,
            )
            WaveSlot(
                state,
                Modifier.placedUnless(atEnd).graphicsLayer { alpha = 1f - state.progress },
                content,
            )
        } else {
            WaveStage(state, atStart, atEnd, source, style, revealedContent, content)
        }
        if (overlay != null) {
            WaveSlot(state, Modifier.matchParentSize().zIndex(1f), overlay, fill = false)
        }
    }
}

/** One of the wave's slots, with the scope that knows the wave. */
@Composable
private fun WaveSlot(
    state: DistortionWaveState,
    modifier: Modifier,
    content: @Composable DistortionWaveScope.() -> Unit,
    fill: Boolean = true,
) {
    Box(modifier, propagateMinConstraints = fill) {
        val scope = remember(this, state) { WaveScope(this, state) }
        scope.content()
    }
}

/** Equal for the same wave, so content that moves from one slot to another is not redone. */
private class WaveScope(
    private val box: BoxScope,
    private val state: DistortionWaveState,
) : DistortionWaveScope, BoxScope by box {
    override fun Modifier.distortionWaveSource(): Modifier = distortionWaveSource(state)

    override fun equals(other: Any?): Boolean =
        other is WaveScope && other.box == box && other.state === state

    override fun hashCode(): Int = 31 * box.hashCode() + state.hashCode()
}

/**
 * Takes a side that cannot be seen out of reach. It is still composed and measured, so it keeps its
 * state and its size. It is not placed, so it is not drawn and cannot be touched or focused, and
 * its semantics are cleared, so it is not in the accessibility tree.
 */
private fun Modifier.placedUnless(hidden: Boolean) =
    (if (hidden) clearAndSetSemantics {} else this).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (!hidden) placeable.place(0, 0) }
    }

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
    revealedContent: @Composable DistortionWaveScope.() -> Unit,
    content: @Composable DistortionWaveScope.() -> Unit,
) {
    // Pixels in one of the style's points.
    val unit = LocalDensity.current.density * style.scale
    // A horizontal wave starts at the start edge: the right one in a right-to-left layout.
    val mirrored =
        style.type == DistortionWaveType.Horizontal &&
            LocalLayoutDirection.current == LayoutDirection.Rtl
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentSource by rememberUpdatedState(source)
    val currentStyle by rememberUpdatedState(style)
    val currentUnit by rememberUpdatedState(unit)
    val currentMirrored by rememberUpdatedState(mirrored)

    val front by
        remember(state) {
            derivedStateOf {
                val u = currentUnit
                val s =
                    currentSource
                        ?: state.sourceInRoot?.translate(-state.positionInRoot)
                        ?: defaultSource(currentStyle.type, size, currentMirrored)
                WaveFront(
                    progress = state.progress,
                    source = Rect(s.left / u, s.top / u, s.right / u, s.bottom / u),
                    size = Size(size.width / u, size.height / u),
                    style = currentStyle,
                    focus = state.focus?.let { it / u },
                    mirrored = currentMirrored,
                )
            }
        }

    val incoming = remember { WaveSide(side = -1f) }
    val outgoing = remember { WaveSide(side = 1f) }
    val band = remember { WaveBand() }
    val particles = remember(style) { List(style.particles.count) { WaveParticle(it, style) } }

    Box(Modifier.matchParentSize().onSizeChanged { size = it })
    WaveSlot(
        state,
        Modifier.placedUnless(atStart).graphicsLayer {
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
        revealedContent,
    )
    WaveSlot(
        state,
        Modifier.placedUnless(atEnd).graphicsLayer {
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
        content,
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
    mirrored: Boolean,
): Rect {
    val centre =
        when (type) {
            DistortionWaveType.Vertical -> Offset(size.width / 2f, size.height.toFloat())
            DistortionWaveType.Horizontal ->
                Offset(if (mirrored) size.width.toFloat() else 0f, size.height / 2f)
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
            set("amplitude", style.lens.amplitude)
            set("lensWidth", style.lens.width)
            set("aberration", style.lens.aberration)
            set("side", side)
        }
        // The most blur anywhere on screen in this frame.
        val blur = style.blur.radius
        val peak = front.energy * blur
        if (!blurred || peak < MIN_BLUR) return waveSideEffect(this.lens, null, null, 0f, 0f)
        val near = minOf(NEAR_BLUR, blur)
        mask(nearMask, front, style, unit, from = 0f, to = near)
        // The second copy only takes over past the first one's blur.
        if (peak <= near) return waveSideEffect(this.lens, nearMask, null, near * unit, 0f)
        mask(farMask, front, style, unit, from = near, to = blur)
        return waveSideEffect(this.lens, nearMask, farMask, near * unit, blur * unit)
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
        mask.set("blurRadius", style.blur.radius)
        mask.set("blurAhead", style.blur.ahead)
        mask.set("blurBehind", style.blur.behind)
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
        set("lead", style.band.lead)
        set("tail", style.band.tail)
        set("strength", style.band.opacity)
        set("rimWidth", style.rim.width)
        set("rimSoftness", style.rim.softness)
        set("cornerRadius", style.cornerRadiusPoints)
        set("unevenness", style.rim.unevenness)
        set("pulse", style.rim.pulse)
        set("hairWidth", style.hairline.width)
        set("hairOpacity", style.hairline.opacity)
        set("hairWhite", style.hairline.white)
        set(
            "lumps",
            WaveRim.lumps(
                front.time,
                front.width,
                front.height,
                style.cornerRadiusPoints,
                style.rim.pulse,
                lumps,
            ),
        )
        brush()
    }
}
