package io.github.amrelmekawy.pdfviewer.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

@Composable
internal actual fun rememberSampleDir(): String =
    remember { NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeFile(
    dir: String,
    name: String,
    bytes: ByteArray,
): String {
    val path = "$dir/$name"
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    check(data.writeToFile(path, atomically = true)) { "Could not write $name" }
    return path
}
