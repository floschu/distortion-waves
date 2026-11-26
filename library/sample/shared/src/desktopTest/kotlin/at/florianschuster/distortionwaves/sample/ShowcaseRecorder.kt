package at.florianschuster.distortionwaves.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Records the showcase video in media/: the four demo pages side by side, each driven by taps on a
 * virtual clock, rendered frame by frame through Skiko.
 *
 * Run with `./gradlew :sample:shared:desktopTest -Pshowcase`, then `media/showcase.sh` to encode
 * the frames.
 */
class ShowcaseRecorder {
    @Test
    fun record() {
        if (System.getProperty("showcase") == null) return
        val out =
            File(System.getProperty("showcase")).apply {
                deleteRecursively()
                mkdirs()
            }

        // The demos leave coroutines waiting on the paused clock. The runner
        // reports them after the last frame, which does not matter here. It
        // reports them in place of a failed step too, so that is kept apart.
        try {
            recordFrames(out)
        } catch (e: AssertionError) {
            if (e::class.simpleName != "UncompletedCoroutinesError") throw e
        }
        failure?.let { throw it }
    }

    private var failure: Throwable? = null

    private fun recordFrames(out: File) {
        runDesktopComposeUiTest(
            width = (CanvasWidth * Scale).toInt(),
            height = (CanvasHeight * Scale).toInt(),
            testTimeout = 2.hours,
        ) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(Scale)) { Showcase() }
            }

            val actions = timeline()
            val frames =
                ((System.getProperty("showcaseSeconds")?.toDouble() ?: Loop) * Fps).roundToInt()
            var next = 0
            for (frame in 0 until frames) {
                val now = frame / Fps
                try {
                    while (next < actions.size && actions[next].first <= now) actions[next++]
                        .second(
                            this,
                        )
                    // An open dropdown is a second root. The first one is the whole scene, and
                    // its picture has the dropdown in it.
                    val root = onAllNodes(isRoot()).onFirst()
                    val image = Image.makeFromBitmap(root.captureToImage().asSkiaBitmap())
                    File(out, "f%04d.png".format(frame))
                        .writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                } catch (e: Throwable) {
                    failure = AssertionError("Frame $frame at $now s failed.", e)
                    return@runDesktopComposeUiTest
                }
                mainClock.advanceTimeBy(
                    (1000.0 * (frame + 1) / Fps).roundToInt() -
                        (1000.0 * frame / Fps).roundToInt().toLong(),
                    ignoreFrameDuration = true,
                )
            }
        }
    }

    private fun SemanticsNodeInteractionsProvider.tap(
        phone: Demo,
        text: String,
    ) = onNode(hasText(text) and hasAnyAncestor(hasTestTag(phone.name))).performClick()

    /**
     * Picks [option] from the dropdown that a tap opened in an earlier frame. Its list is the last
     * thing on screen. The list is only there a frame after the tap, so this is a step of its own.
     */
    private fun SemanticsNodeInteractionsProvider.choose(option: String) =
        onAllNodes(hasText(option)).onLast().performClick()

    /** A tap at a point on a phone, in the phone's own dp. */
    private fun SemanticsNodeInteractionsProvider.tapAt(
        phone: Demo,
        x: Float,
        y: Float,
    ) =
        onNode(hasTestTag(phone.name)).performTouchInput {
            click(
                Offset(
                    x * PhoneDensity * Scale,
                    y * PhoneDensity * Scale,
                ),
            )
        }

    /** What happens when, in seconds. The loop ends where it started. */
    private fun timeline(): List<Pair<Double, SemanticsNodeInteractionsProvider.() -> Unit>> =
        listOf<Pair<Double, SemanticsNodeInteractionsProvider.() -> Unit>>(
                0.3 to { tap(Demo.Onboarding, "Next") },
                0.5 to { tap(Demo.SendMoney, "Confirm") },
                0.8 to { tapAt(Demo.Playground, PhoneWidth / 2f, 240f) },
                1.2 to { tap(Demo.Payment, "Pay") },
                // The playground's first wave has gone by 4.4 s.
                3.5 to { tap(Demo.Playground, "Vertical") },
                3.8 to { choose("Horizontal") },
                4.0 to { tap(Demo.Playground, "Colorful") },
                4.3 to { choose("Ocean") },
                // A frame after the list has closed: an open list takes the next tap anywhere.
                4.4 to { tap(Demo.Onboarding, "Next") },
                4.6 to { tapAt(Demo.Playground, PhoneWidth / 2f, 240f) },
                6.0 to { tap(Demo.SendMoney, "Done") },
                7.5 to { tap(Demo.Payment, "Done") },
                8.3 to { tap(Demo.Onboarding, "Start over") },
                // Back to where the loop started.
                8.6 to { tap(Demo.Playground, "Horizontal") },
                8.9 to { choose("Vertical") },
                9.1 to { tap(Demo.Playground, "Ocean") },
                9.4 to { choose("Colorful") },
            )
            .sortedBy { it.first }
}

private const val Fps = 30.0
private const val Loop = 12.0

/** The canvas in dp, rendered at [Scale] px per dp. */
private const val CanvasWidth = 1920f
private const val CanvasHeight = 1080f
private const val Scale = 2f

/** Each phone is a 391 × 831 dp screen, shrunk to [PhoneSlot] dp wide on the canvas. */
private const val PhoneWidth = 391.43f
private const val PhoneHeight = 831.43f
private const val PhoneSlot = 340f
private const val PhoneDensity = PhoneSlot / PhoneWidth

@Composable
private fun Showcase() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt("Distortion Waves", size = 60, weight = FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Txt(
            "A colourful wave that carries one view into the next. Compose Multiplatform.",
            color = Dark.faint,
            size = 26,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            for (demo in Demo.entries) Phone(demo)
        }
    }
}

@Composable
private fun Phone(demo: Demo) {
    val shape = RoundedCornerShape(40.dp)
    Box(
        Modifier.size(PhoneSlot.dp, (PhoneHeight * PhoneDensity).dp)
            .clip(shape)
            .background(Color.Black)
            .testTag(demo.name),
    ) {
        val outer = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(outer.density * PhoneDensity, outer.fontScale),
        ) {
            DemoPage(demo)
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color(0x26FFFFFF), shape))
    }
}
