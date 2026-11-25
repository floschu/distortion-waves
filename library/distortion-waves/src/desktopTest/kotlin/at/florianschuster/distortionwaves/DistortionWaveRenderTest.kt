package at.florianschuster.distortionwaves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Renders the wave off screen through Skiko: the shaders must compile, and a frame in the middle of
 * the wave must carry its colour. Each frame is also written to build/wave-frames for a look.
 */
class DistortionWaveRenderTest {
    @Test
    fun vertical() = render("vertical", DistortionWaveStyle.Vertical, Rect(40f, 1500f, 746f, 1616f))

    @Test
    fun horizontal() =
        render("horizontal", DistortionWaveStyle.Horizontal, Rect(0f, 600f, 48f, 1100f))

    @Test fun center() = render("center", DistortionWaveStyle.Center, Rect(329f, 300f, 457f, 428f))

    private fun render(
        name: String,
        style: DistortionWaveStyle,
        source: Rect,
    ) {
        val state = DistortionWaveState(initiallyRevealed = false)
        ImageComposeScene(width = 786, height = 1664, density = Density(2f)) {
                DistortionWave(
                    state = state,
                    style = style,
                    source = source,
                    reducedMotion = ReducedMotion.Never,
                    modifier = Modifier.fillMaxSize(),
                    revealedContent = { Screen(Color(0xFF0D1B12), "Sent") },
                    content = { Screen(Color(0xFF0D0D0D), "Review transfer") },
                )
            }
            .use { scene ->
                val rest = scene.render(0).colourfulness()
                for (progress in listOf(0.1f, 0.25f, 0.4f)) {
                    runBlocking { state.scrubTo(progress) }
                    val frame = scene.render(1_000_000L * (progress * 1000).toLong())
                    File("build/wave-frames").mkdirs()
                    File("build/wave-frames/$name-$progress.png")
                        .writeBytes(frame.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    if (progress == 0.25f) {
                        assertTrue(
                            frame.colourfulness() > rest + 5.0,
                            "The wave's colour is missing from the $name frame.",
                        )
                    }
                }
            }
    }

    /** The mean spread between the colour channels: near zero for a grey screen. */
    private fun Image.colourfulness(): Double {
        val pixels = peekPixels()!!
        var total = 0.0
        var count = 0
        for (y in 0 until height step 8) {
            for (x in 0 until width step 8) {
                val c = pixels.getColor(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                total += maxOf(r, g, b) - minOf(r, g, b)
                count++
            }
        }
        return total / count
    }
}

@Composable
private fun Screen(
    background: Color,
    title: String,
) {
    Column(Modifier.fillMaxSize().background(background).padding(24.dp)) {
        BasicText(title, style = TextStyle(color = Color(0xFFBBBBBB), fontSize = 28.sp))
        repeat(12) {
            BasicText(
                "Row $it",
                Modifier.padding(top = 24.dp),
                style = TextStyle(color = Color(0xFF888888), fontSize = 18.sp),
            )
        }
    }
}
