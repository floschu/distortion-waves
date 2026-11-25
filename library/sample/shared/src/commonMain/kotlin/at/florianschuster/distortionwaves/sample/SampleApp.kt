package at.florianschuster.distortionwaves.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/** The demos, one page each. */
enum class Demo(
    val title: String,
) {
    SendMoney("Send"),
    Onboarding("Onboarding"),
    Payment("Payment"),
    Playground("Playground"),
}

/**
 * The whole sample: the demos side by side in a pager. Swipe between them or pick one from the tabs
 * at the top. The wave only plays inside a demo.
 *
 * The pager draws into a layer. The top bar draws that layer again, blurred, so whatever scrolls
 * under the bar shows through it frosted.
 */
@Composable
fun SampleApp() {
    val pager = rememberPagerState { Demo.entries.size }
    val scope = rememberCoroutineScope()
    val pages = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize().background(Dark.page)) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().recordInto(pages),
            key = { it },
        ) { page ->
            DemoPage(Demo.entries[page])
        }
        Tabs(
            position = { pager.currentPage + pager.currentPageOffsetFraction },
            modifier = Modifier.frosted(pages),
        ) {
            scope.launch { pager.animateScrollToPage(it) }
        }
    }
}

/** The repository link over one segment per demo, all the same width, at the top of the page. */
@Composable
private fun Tabs(
    position: () -> Float,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .padding(Margin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RepoLink()
        Spacer(Modifier.height(Gap))
        Segments(position, onSelect)
    }
}

/** The logo and the repository's name. Opens the repository on GitHub. */
@Composable
private fun RepoLink() {
    val uri = LocalUriHandler.current
    Row(
        Modifier.height(LogoHeight)
            .clip(CircleShape)
            .clickable { uri.openUri(RepoUrl) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(Modifier.height(18.dp))
        Spacer(Modifier.width(8.dp))
        Txt("floschu/distortion-waves", color = Dark.ink, size = 15, weight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Txt("samples", color = Dark.faint, size = 15)
    }
}

private const val RepoUrl = "https://github.com/floschu/distortion-waves"

@Composable
private fun Segments(
    position: () -> Float,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(CircleShape)
            .background(Dark.card.copy(alpha = 0.6f))
            .border(1.dp, Dark.line, CircleShape)
            .padding(4.dp)
            // The pill under the selected tab follows the pager, mid-swipe too.
            .drawBehind {
                val width = size.width / Demo.entries.size
                drawRoundRect(
                    Dark.ink,
                    topLeft = Offset(width * position(), 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            },
    ) {
        for (demo in Demo.entries) {
            // 1 on the selected tab, fading to 0 one tab away.
            val on by remember {
                derivedStateOf { (1f - abs(position() - demo.ordinal)).coerceIn(0f, 1f) }
            }
            Box(
                Modifier.weight(1f)
                    .clip(CircleShape)
                    .clickable { onSelect(demo.ordinal) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    demo.title,
                    color = lerp(Dark.faint, Dark.page, on),
                    size = 13,
                    weight = FontWeight.Medium,
                )
            }
        }
    }
}

/** One demo, filling the page. */
@Composable
internal fun DemoPage(demo: Demo) {
    when (demo) {
        Demo.SendMoney -> SendMoneyDemo()
        Demo.Onboarding -> OnboardingDemo()
        Demo.Payment -> PaymentDemo()
        Demo.Playground -> PlaygroundDemo()
    }
}

private val LogoHeight = 32.dp

/** The tab row: 8 dp around 13 sp text, inside 4 dp of padding. */
private val TabsHeight = 42.dp

/** Space for the logo and the tabs at the top of a page. */
internal val TopBar = Margin * 2 + LogoHeight + Gap + TabsHeight
