package io.github.amrelmekawy.pdfviewer.sample

import androidx.compose.runtime.Composable

/** A directory the sample may write its demo files to. */
@Composable
internal expect fun rememberSampleDir(): String

/** Writes [bytes] to [name] in [dir] and returns the absolute path. */
internal expect fun writeFile(
    dir: String,
    name: String,
    bytes: ByteArray,
): String
