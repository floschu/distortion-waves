package at.florianschuster.distortionwaves.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The sample as a view controller: return it from a `UIViewControllerRepresentable` in an Xcode
 * app.
 */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }
