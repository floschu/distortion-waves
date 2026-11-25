package at.florianschuster.distortionwaves

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.collectLatest

/**
 * Like `Crossfade`, with a wave: when [targetState] changes, a wave carries the view for the old
 * state into the view for the new one.
 *
 * Going back to the state on show while the wave is still out pulls the wave back into its source.
 * Moving on to a third state swaps in the new view and lets the wave carry on.
 *
 * Use it to give any component, your own or a Material one, a wave between its states. Give
 * [content] an opaque background, as with [DistortionWave].
 *
 * [targetState] is compared with `equals`. The view for the new state is composed when the wave
 * starts. When the wave has gone, the view for the old state leaves the composition and the new
 * state's view is composed again in its place, so hoist any state that has to outlive the wave.
 *
 * @param source the frame the wave opens from, in this composable's own coordinates, in pixels.
 *   Without one, a point on the edge the wave's type starts from.
 * @param focus where the source was touched, in the same coordinates.
 * @param reducedMotion whether the wave gives way to a crossfade. By default, when the system asks
 *   for less motion.
 * @param overlay stays above the wave, sharp and live: the place for the control that changes the
 *   state.
 * @param content the view for a state. It is called for two states at once while the wave runs.
 */
@Composable
public fun <T> DistortionWaveContent(
    targetState: T,
    modifier: Modifier = Modifier,
    style: DistortionWaveStyle = DistortionWaveStyle.Center,
    source: Rect? = null,
    focus: Offset? = null,
    reducedMotion: ReducedMotion = ReducedMotion.System,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(state: T) -> Unit,
) {
    val state = remember { DistortionWaveState() }
    var shown by remember { mutableStateOf(targetState) }
    var incoming by remember { mutableStateOf(targetState) }
    var container by remember { mutableStateOf(Offset.Zero) }
    val currentTarget by rememberUpdatedState(targetState)
    val currentFocus by rememberUpdatedState(focus)

    LaunchedEffect(state) {
        snapshotFlow { currentTarget }
            .collectLatest { target ->
                if (target == shown) {
                    state.collapse()
                    incoming = target
                    return@collectLatest
                }
                incoming = target
                state.reveal(currentFocus?.let { it + container })
                shown = target
                state.snapTo(revealed = false)
            }
    }

    DistortionWave(
        state = state,
        modifier = modifier.onGloballyPositioned { container = it.positionInRoot() },
        style = style,
        source = source,
        reducedMotion = reducedMotion,
        overlay = overlay,
        // At rest there is one view, not two of the same state.
        revealedContent = { if (incoming != shown) key(incoming) { content(incoming) } },
        content = { key(shown) { content(shown) } },
    )
}
