package at.florianschuster.distortionwaves.sample

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * The swell from the app icon: one band of the wave with its colour split. Keep it in step with the
 * launcher icon and media/logo.svg.
 */
private const val Swell =
    "M16,64 C28,38 48,30 62,46 C72,58 80,66 92,58 C80,74 66,70 56,58 C44,46 30,46 16,64 Z"

private val LogoVector: ImageVector by lazy {
    val nodes = addPathNodes(Swell)
    ImageVector.Builder(
            name = "Logo",
            defaultWidth = 80.dp,
            defaultHeight = 52.dp,
            viewportWidth = 80f,
            viewportHeight = 52f,
        )
        .apply {
            group(translationX = -14f, translationY = -26f) {
                group(translationX = -1f, translationY = -3.5f) {
                    addPath(nodes, fill = SolidColor(Color(0xFFB428E6)))
                }
                group(translationX = 1f, translationY = 3.5f) {
                    addPath(nodes, fill = SolidColor(Color(0xFF5ABEFF)))
                }
                addPath(
                    nodes,
                    fill =
                        Brush.linearGradient(
                            0f to Color(0xFFFFC915),
                            0.5f to Color(0xFFEB64A0),
                            1f to Color(0xFF7EC4FF),
                            start = Offset(16f, 40f),
                            end = Offset(92f, 64f),
                        ),
                )
            }
        }
        .build()
}

@Composable
internal fun Logo(modifier: Modifier = Modifier) {
    Image(LogoVector, contentDescription = "Distortion Waves", modifier = modifier)
}
