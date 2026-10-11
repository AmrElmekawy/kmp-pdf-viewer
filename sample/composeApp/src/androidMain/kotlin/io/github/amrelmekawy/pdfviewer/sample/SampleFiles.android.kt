package io.github.amrelmekawy.pdfviewer.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
internal actual fun rememberSampleDir(): String {
    val context = LocalContext.current
    return remember(context) { context.filesDir.absolutePath }
}

internal actual fun writeFile(
    dir: String,
    name: String,
    bytes: ByteArray,
): String = File(dir, name).apply { writeBytes(bytes) }.absolutePath
