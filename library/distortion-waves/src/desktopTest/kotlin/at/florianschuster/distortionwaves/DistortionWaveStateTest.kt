package at.florianschuster.distortionwaves

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The state's logic, on the crossfade the wave falls back to. It runs the same calls as the wave
 * and needs no shader, so these tests are quick.
 */
@OptIn(ExperimentalTestApi::class)
class DistortionWaveStateTest {
    @Test
    fun `reveal ends on the revealed side and collapse on the other`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Wave(state)
        }

        scope.launch { state.reveal() }
        mainClock.advanceTimeBy(Whole)
        assertEquals(1f, state.progress)
        assertTrue(state.revealed)

        scope.launch { state.collapse() }
        mainClock.advanceTimeBy(Whole)
        assertEquals(0f, state.progress)
        assertFalse(state.revealed)
    }

    @Test
    fun `toggle turns a running wave around`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Wave(state)
        }

        scope.launch { state.toggle() }
        mainClock.advanceTimeBy(100)
        assertTrue(state.isRunning)
        assertTrue(state.revealed)
        val turnedAt = state.progress
        assertTrue(turnedAt > 0f && turnedAt < 1f)

        scope.launch { state.toggle() }
        mainClock.advanceTimeBy(50)
        assertFalse(state.revealed)
        assertTrue(state.progress < turnedAt)

        mainClock.advanceTimeBy(Whole)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `a call that takes over from play is not stopped by it`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Wave(state)
        }

        scope.launch { state.play() }
        mainClock.advanceTimeBy(100)
        var finished = false
        scope.launch {
            state.reveal()
            finished = true
        }
        mainClock.advanceTimeBy(Whole)

        assertTrue(finished)
        assertEquals(1f, state.progress)
    }

    @Test
    fun `play goes back to rest`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Wave(state)
        }
        scope.launch { state.play() }
        mainClock.advanceTimeBy(Whole)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `the boolean overload reports each finished run`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var revealed by mutableStateOf(false)
        val finished = mutableListOf<Boolean>()
        setContent {
            DistortionWave(
                revealed = revealed,
                reducedMotion = ReducedMotion.Always,
                onFinish = { finished += it },
                revealedContent = { BasicText("After") },
                content = { BasicText("Before") },
            )
        }
        mainClock.advanceTimeBy(Whole)
        assertEquals(emptyList(), finished)
        revealed = true
        mainClock.advanceTimeBy(Whole)
        revealed = false
        mainClock.advanceTimeBy(Whole)
        assertEquals(listOf(true, false), finished)
    }

    @Test
    fun `content composes one view at rest and two during the wave`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var target by mutableStateOf("a")
        val composed = mutableListOf<String>()
        setContent {
            DistortionWaveContent(target, reducedMotion = ReducedMotion.Always) { state ->
                DisposableEffect(Unit) {
                    composed += state
                    onDispose { composed -= state }
                }
                BasicText(state)
            }
        }
        mainClock.advanceTimeBy(Whole)
        assertEquals(listOf("a"), composed)

        target = "b"
        mainClock.advanceTimeBy(100)
        assertEquals(listOf("a", "b"), composed)

        mainClock.advanceTimeBy(Whole)
        assertEquals(listOf("b"), composed)
    }
}

/** Longer than the crossfade these tests run on, in milliseconds. */
private const val Whole = 1_000L

@Composable
private fun Wave(state: DistortionWaveState) {
    DistortionWave(
        state = state,
        reducedMotion = ReducedMotion.Always,
        revealedContent = { BasicText("After") },
        content = { BasicText("Before") },
    )
}
