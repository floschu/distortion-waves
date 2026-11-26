package at.florianschuster.distortionwaves.internal

import androidx.compose.runtime.Composable

/** Follows the browser's `prefers-reduced-motion`, read each time the wave is composed. */
@Composable internal actual fun systemReducedMotion(): Boolean = prefersReducedMotion()

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")
