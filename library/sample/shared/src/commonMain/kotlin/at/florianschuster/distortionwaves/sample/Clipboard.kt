package at.florianschuster.distortionwaves.sample

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry that holds [text]. Each platform builds it its own way. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
