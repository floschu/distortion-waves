package at.florianschuster.distortionwaves.sample

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.florianschuster.distortionwaves.DistortionWave
import at.florianschuster.distortionwaves.DistortionWavePalette
import at.florianschuster.distortionwaves.DistortionWaveStyle
import at.florianschuster.distortionwaves.distortionWaveSource
import at.florianschuster.distortionwaves.rememberDistortionWaveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vertical, colourful: the plain two-view case. The button sits in the overlay, so it stays sharp,
 * and the wave leans toward where it was pressed.
 */
@Composable
internal fun SendMoneyDemo() {
    val state = rememberDistortionWaveState()
    val scope = rememberCoroutineScope()
    val sent by remember { derivedStateOf { state.progress > 0.25f } }

    DistortionWave(
        state = state,
        modifier = Modifier.fillMaxSize(),
        style = DistortionWaveStyle.Vertical,
        overlay = {
            PillButton(
                if (sent) "Done" else "Confirm",
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Margin)
                    .fillMaxWidth()
                    .distortionWaveSource(state),
            ) {
                scope.launch { state.toggle() }
            }
        },
        revealedContent = {
            Page(Color(0xFF0C1A10)) {
                Spacer(Modifier.height(48.dp))
                Badge("✓", Success, 84.dp, Modifier.align(Alignment.CenterHorizontally))
                Txt(
                    "$1,250.00",
                    Modifier.fillMaxWidth(),
                    size = 40,
                    weight = FontWeight.SemiBold,
                    align = TextAlign.Center,
                )
                Txt(
                    "sent to Maya Chen",
                    Modifier.fillMaxWidth(),
                    color = Dark.faint,
                    size = 18,
                    align = TextAlign.Center,
                )
                Card {
                    DetailRow("Reference", "TX-90412-A")
                    DetailRow("Arrived", "Just now")
                }
            }
        },
        content = {
            Page(Dark.page) {
                Spacer(Modifier.height(24.dp))
                Badge("MC", Color(0xFF2F6F8F), 72.dp, Modifier.align(Alignment.CenterHorizontally))
                Txt(
                    "Sending to Maya Chen",
                    Modifier.fillMaxWidth(),
                    color = Dark.faint,
                    align = TextAlign.Center,
                )
                Txt(
                    "$1,250.00",
                    Modifier.fillMaxWidth(),
                    size = 52,
                    weight = FontWeight.SemiBold,
                    align = TextAlign.Center,
                )
                Card {
                    DetailRow("From", "Everyday ·· 4821")
                    DetailRow("Arrives", "In seconds")
                    DetailRow("Fee", "Free")
                    DetailRow("Note", "Studio rent")
                }
            }
        },
    )
}

/**
 * Center, green: a ring spreads from the token inside a card while the token itself, held sharp at
 * the ring's centre, turns into the check.
 */
@Composable
internal fun PaymentDemo() {
    val state = rememberDistortionWaveState()
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Dark.page)
            .verticalScroll(rememberScrollState())
            .padding(Margin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        DistortionWave(
            state = state,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth().card(),
            style = DistortionWaveStyle.Center.copy(cornerRadius = CardCorner),
            revealedContent = {
                Sheet(paid = true, label = "Done", enabled = true) {
                    scope.launch { state.toggle() }
                }
            },
            content = {
                Sheet(
                    paid = false,
                    label = if (processing) "Processing…" else "Pay",
                    enabled = !processing,
                    badge = Modifier.distortionWaveSource(state),
                ) {
                    scope.launch {
                        // Mid-wave, both buttons turn the wave around.
                        if (state.isRunning) return@launch state.toggle()
                        processing = true
                        delay(900)
                        processing = false
                        state.reveal()
                    }
                }
            },
        )
    }
}

@Composable
private fun Sheet(
    paid: Boolean,
    label: String,
    enabled: Boolean,
    badge: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Dark.card).padding(Margin),
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Gap))
        Badge(if (paid) "✓" else "$", if (paid) Success else Color(0xFF2775CA), 64.dp, badge)
        Txt(if (paid) "Paid" else "Pay Northwind Coffee", color = Dark.faint)
        Txt("11.054 USDC", size = 36, weight = FontWeight.Medium)
        Txt("Oct 3, 2026 at 8:55 AM", color = Dark.faint, size = 15)
        // Plain rows, not a card in a card.
        Column(Modifier.fillMaxWidth()) {
            DetailRow("To", "HTjz…sUjs")
            DetailRow("Network fee", "0.00206 SOL")
        }
        PillButton(label, Modifier.fillMaxWidth(), enabled = enabled, onClick = onClick)
    }
}

private val Pages =
    listOf(
        Triple(Color(0xFF1B1030), "Collect", "Save anything you find. It lands in one place."),
        Triple(Color(0xFF2A130F), "Arrange", "Drag things into boards. Nothing is ever lost."),
        Triple(Color(0xFF0E2230), "Share", "Send a board to anyone. They see it as you do."),
    )

/** One wave from page [from] to page [to]. */
private class Hop(
    val from: Int,
    val to: Int,
) {
    var started by mutableStateOf(false)
    var done by mutableStateOf(false)
}

private val OnboardingStyle =
    DistortionWaveStyle.Horizontal.copy(palette = DistortionWavePalette.Sunset)

/**
 * Horizontal, sunset: Next sends the next page across from the left edge. Tapping Next again before
 * the wave has gone starts the next wave right away, behind the first one: each new wave lives in
 * the revealed side of the wave before it, so both fronts cross the screen at once. When every wave
 * has gone, the last page becomes the plain page again. The button and the dots sit above the
 * waves, so they stay sharp.
 */
@Composable
internal fun OnboardingDemo() {
    var page by remember { mutableIntStateOf(0) }
    val hops = remember { mutableStateListOf<Hop>() }
    // The page the newest wave is headed to.
    val shown = hops.lastOrNull()?.to ?: page

    Box(Modifier.fillMaxSize()) {
        Waves(page, hops, 0) {
            if (hops.all { it.done }) {
                page = hops.last().to
                hops.clear()
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Margin)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pages.indices.forEach {
                    val on = it == shown
                    val width by animateDpAsState(if (on) 24.dp else 8.dp)
                    val alpha by animateFloatAsState(if (on) 0.9f else 0.35f)
                    Box(
                        Modifier.size(width, 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = alpha)),
                    )
                }
            }
            PillButton(
                if (shown == Pages.lastIndex) "Start over" else "Next",
                Modifier.width(160.dp),
            ) {
                // A few waves at once at most: each one is another blurred layer.
                if (hops.count { !it.done } < MaxHops) hops += Hop(shown, (shown + 1) % Pages.size)
            }
        }
    }
}

private const val MaxHops = 3

/**
 * The wave at [i] and, in its revealed side, every wave after it. New waves go at the deepest
 * point, so the ones already running keep their place and play on undisturbed.
 */
@Composable
private fun Waves(
    page: Int,
    hops: List<Hop>,
    i: Int,
    onFinish: () -> Unit,
) {
    val hop = hops.getOrNull(i)
    if (hop == null) {
        OnboardingPage(page)
        return
    }
    LaunchedEffect(hop) { hop.started = true }
    DistortionWave(
        revealed = hop.started,
        modifier = Modifier.fillMaxSize(),
        style = OnboardingStyle,
        onFinish = { revealed ->
            if (revealed) {
                hop.done = true
                onFinish()
            }
        },
        revealedContent = { Waves(hop.to, hops, i + 1, onFinish) },
        content = { OnboardingPage(hop.from) },
    )
}

@Composable
private fun OnboardingPage(index: Int) {
    val (colour, title, body) = Pages[index]
    Page(colour) {
        Spacer(Modifier.height(120.dp))
        Txt("0${index + 1}", color = Color(0x80FFFFFF), size = 18, weight = FontWeight.Medium)
        Txt(title, size = 56, weight = FontWeight.SemiBold)
        Txt(body, color = Color(0xCCFFFFFF), size = 20)
    }
}

/** A full page with the sample's margins, under the tabs. */
@Composable
internal fun Page(
    colour: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(colour)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = Margin, end = Margin, top = TopBar),
        verticalArrangement = Arrangement.spacedBy(Gap),
        content = content,
    )
}
