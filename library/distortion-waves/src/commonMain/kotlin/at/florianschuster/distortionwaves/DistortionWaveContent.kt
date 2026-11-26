package at.florianschuster.distortionwaves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
 * starts and keeps what it remembers when the wave has gone. The view for the old state then leaves
 * the composition.
 *
 * @param source the frame the wave opens from, in this composable's own coordinates, in pixels.
 *   Without one, the element marked with [DistortionWaveScope.distortionWaveSource], and without
 *   that either, a point on the edge the wave's type starts from.
 * @param focus where the source was touched, in the same coordinates. Without one, the last press
 *   on the marked element.
 * @param motion whether the wave gives way to a crossfade. By default, when the system asks for
 *   less motion.
 * @param onFinish called with the state on show when a wave has gone, or has been pulled back.
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
    motion: DistortionWaveMotion = DistortionWaveMotion.System,
    onFinish: (state: T) -> Unit = {},
    overlay: (@Composable DistortionWaveScope.() -> Unit)? = null,
    content: @Composable DistortionWaveScope.(state: T) -> Unit,
) {
    val state = remember { DistortionWaveState() }
    var shown by remember { mutableStateOf(targetState) }
    var incoming by remember { mutableStateOf(targetState) }
    val currentTarget by rememberUpdatedState(targetState)
    val currentFocus by rememberUpdatedState(focus)
    val currentOnFinish by rememberUpdatedState(onFinish)
    val currentContent by rememberUpdatedState(content)

    // One movable view per state on screen. When the wave has gone, the new
    // state's view moves from the revealed side to the resting side with
    // everything it remembers.
    val views = remember { mutableMapOf<T, @Composable DistortionWaveScope.() -> Unit>() }
    views.keys.retainAll { it == shown || it == incoming }
    val view: (T) -> @Composable DistortionWaveScope.() -> Unit = { of ->
        views.getOrPut(of) {
            movableContentWithReceiverOf<DistortionWaveScope> { currentContent(of) }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { currentTarget }
            .collectLatest { target ->
                if (target == shown) {
                    val wasOut = state.progress > 0f
                    state.collapse()
                    incoming = target
                    if (wasOut) currentOnFinish(target)
                    return@collectLatest
                }
                incoming = target
                state.reveal(currentFocus)
                shown = target
                state.snapTo(revealed = false)
                currentOnFinish(target)
            }
    }

    DistortionWave(
        state = state,
        modifier = modifier,
        style = style,
        source = source,
        motion = motion,
        overlay = overlay,
        // At rest there is one view, not two of the same state.
        revealedContent = { if (incoming != shown) view(incoming)() },
        content = { view(shown)() },
    )
}
