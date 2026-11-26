package at.florianschuster.distortionwaves

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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
                motion = DistortionWaveMotion.Reduced,
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
            DistortionWaveContent(target, motion = DistortionWaveMotion.Reduced) { state ->
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

    @Test
    fun `content calls onFinish with the state on show`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var target by mutableStateOf("a")
        val finished = mutableListOf<String>()
        setContent {
            DistortionWaveContent(
                target,
                motion = DistortionWaveMotion.Reduced,
                onFinish = { finished += it },
            ) { state ->
                BasicText(state)
            }
        }
        mainClock.advanceTimeBy(Whole)
        assertEquals(emptyList(), finished)

        target = "b"
        mainClock.advanceTimeBy(Whole)
        assertEquals(listOf("b"), finished)

        // Pulled back mid-wave, it ends on the state it never left.
        target = "c"
        mainClock.advanceTimeBy(100)
        target = "b"
        mainClock.advanceTimeBy(Whole)
        assertEquals(listOf("b", "b"), finished)
    }

    @Test
    fun `content keeps what the new view remembers when the wave has gone`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var target by mutableStateOf("a")
        var made = 0
        setContent {
            DistortionWaveContent(target, motion = DistortionWaveMotion.Reduced) { state ->
                val id = remember { ++made }
                BasicText("$state $id")
            }
        }
        mainClock.advanceTimeBy(Whole)
        target = "b"
        mainClock.advanceTimeBy(Whole)

        onNodeWithText("b 2").assertExists()
        assertEquals(2, made)
    }

    @Test
    fun `scrubbing and snapping need no coroutine and stop a running wave`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            Wave(state)
        }

        scope.launch { state.reveal() }
        mainClock.advanceTimeBy(100)
        assertTrue(state.isRunning)

        state.scrubTo(0.5f)
        assertFalse(state.isRunning)
        mainClock.advanceTimeBy(Whole)
        assertEquals(0.5f, state.progress)
        assertFalse(state.revealed)

        state.snapTo(revealed = true)
        assertEquals(1f, state.progress)
        assertTrue(state.revealed)
    }

    @Test
    fun `the wave itself runs to the end and back`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = DistortionWaveState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            DistortionWave(
                state = state,
                modifier = Modifier.size(200.dp),
                motion = DistortionWaveMotion.Full,
                revealedContent = { BasicText("After") },
                content = { BasicText("Before") },
            )
        }
        val whole = (state.duration * 1000).toLong() + 100

        scope.launch { state.reveal() }
        mainClock.advanceTimeBy(whole / 2)
        assertTrue(state.isRunning)
        assertTrue(state.progress > 0.3f && state.progress < 0.7f)
        mainClock.advanceTimeBy(whole)
        assertEquals(1f, state.progress)
        assertFalse(state.isRunning)

        scope.launch { state.collapse() }
        mainClock.advanceTimeBy(whole)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `a slot marks the source of the boolean overload's wave`() = runComposeUiTest {
        var marked: DistortionWaveState? = null
        setContent {
            // The state overload, to look at what the scope's modifier wrote.
            val state = remember { DistortionWaveState() }.also { marked = it }
            DistortionWave(
                state = state,
                modifier = Modifier.padding(10.dp).size(200.dp),
                motion = DistortionWaveMotion.Reduced,
                overlay = {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .size(100.dp, 40.dp)
                            .distortionWaveSource(),
                    )
                },
                revealedContent = { BasicText("After") },
                content = { BasicText("Before") },
            )
        }
        waitForIdle()
        val source = marked!!.sourceInRoot!!.translate(-marked!!.positionInRoot)
        assertEquals(Rect(50f, 160f, 150f, 200f), source)
    }
}

/**
 * The side that is not on show is still composed, but nothing can reach it: no tap, no key and no
 * accessibility service.
 */
@OptIn(ExperimentalTestApi::class)
class DistortionWaveHiddenSideTest {
    @Test
    fun `a tap on the revealed side does not reach the content under it`() = runComposeUiTest {
        var hiddenClicks = 0
        setContent {
            DistortionWave(
                revealed = true,
                modifier = Modifier.size(200.dp),
                motion = DistortionWaveMotion.Reduced,
                revealedContent = { BasicText("After", Modifier.fillMaxSize()) },
                content = { Box(Modifier.fillMaxSize().clickable { hiddenClicks++ }) },
            )
        }
        onRoot().performTouchInput { click(center) }
        waitForIdle()
        assertEquals(0, hiddenClicks)
    }

    @Test
    fun `a tap on the content does not reach the revealed side under it`() = runComposeUiTest {
        var hiddenClicks = 0
        var shownClicks = 0
        setContent {
            DistortionWave(
                revealed = false,
                modifier = Modifier.size(200.dp),
                motion = DistortionWaveMotion.Reduced,
                revealedContent = { Box(Modifier.fillMaxSize().clickable { hiddenClicks++ }) },
                content = {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(50.dp).clickable { shownClicks++ })
                    }
                },
            )
        }
        onRoot().performTouchInput { click(center) }
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        waitForIdle()
        assertEquals(0, hiddenClicks)
        assertEquals(1, shownClicks)
    }

    @Test
    fun `the hidden side takes no keyboard focus and is not in the semantics`() = runComposeUiTest {
        var hiddenFocused = false
        var shownFocused = false
        setContent {
            DistortionWave(
                revealed = false,
                modifier = Modifier.size(200.dp),
                motion = DistortionWaveMotion.Reduced,
                revealedContent = {
                    Box(Modifier.onFocusChanged { hiddenFocused = it.isFocused }.focusable()) {
                        BasicText("After")
                    }
                },
                content = {
                    Box(Modifier.onFocusChanged { shownFocused = it.isFocused }.focusable()) {
                        BasicText("Before")
                    }
                },
            )
        }
        onNodeWithText("Before").assertExists()
        onNodeWithText("After").assertDoesNotExist()
        repeat(3) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            assertFalse(hiddenFocused)
        }
        assertTrue(shownFocused)
    }

    @Test
    fun `the wave's own path hides the resting side too`() = runComposeUiTest {
        var hiddenClicks = 0
        setContent {
            DistortionWave(
                revealed = false,
                modifier = Modifier.size(200.dp),
                motion = DistortionWaveMotion.Full,
                revealedContent = { Box(Modifier.fillMaxSize().clickable { hiddenClicks++ }) },
                content = { BasicText("Before", Modifier.fillMaxSize()) },
            )
        }
        onRoot().performTouchInput { click(center) }
        waitForIdle()
        assertEquals(0, hiddenClicks)
    }
}

/** Longer than the crossfade these tests run on, in milliseconds. */
private const val Whole = 1_000L

@Composable
private fun Wave(state: DistortionWaveState) {
    DistortionWave(
        state = state,
        motion = DistortionWaveMotion.Reduced,
        revealedContent = { BasicText("After") },
        content = { BasicText("Before") },
    )
}
