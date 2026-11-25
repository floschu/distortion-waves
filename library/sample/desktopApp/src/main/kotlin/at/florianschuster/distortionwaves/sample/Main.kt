package at.florianschuster.distortionwaves.sample

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.imageio.ImageIO

private val Icon =
    Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use {
        BitmapPainter(ImageIO.read(it).toComposeImageBitmap())
    }

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Distortion Waves samples",
        icon = Icon,
        state = rememberWindowState(size = DpSize(420.dp, 880.dp)),
    ) {
        SampleApp()
    }
}
