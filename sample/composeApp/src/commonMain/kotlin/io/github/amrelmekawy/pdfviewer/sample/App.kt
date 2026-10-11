package io.github.amrelmekawy.pdfviewer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amrelmekawy.pdfviewer.PdfLoadState
import io.github.amrelmekawy.pdfviewer.PdfSource
import io.github.amrelmekawy.pdfviewer.PdfViewer
import io.github.amrelmekawy.pdfviewer.PdfViewerState
import io.github.amrelmekawy.pdfviewer.rememberPdfViewerState
import io.github.amrelmekawy.pdfviewer.sample.resources.Res
import kotlinx.coroutines.launch

/** Each demo shows one source or one way to fail. */
private enum class Demo(
    val label: String,
) {
    LocalFile("Local file"),
    Bytes("Bytes"),
    Remote("Remote"),
    NotFound("Missing file"),
    Http404("HTTP 404"),
    Unreachable("No connection"),
    Invalid("Invalid file"),
    Password("Password"),
    TooLarge("Too large"),
}

private class SampleFiles(
    val sample: ByteArray,
    val password: ByteArray,
    val samplePath: String,
    val passwordPath: String,
)

@Composable
fun App() {
    val dir = rememberSampleDir()
    val files by produceState<SampleFiles?>(null, dir) {
        val sample = Res.readBytes("files/sample.pdf")
        val password = Res.readBytes("files/password.pdf")
        value = SampleFiles(sample, password, writeFile(dir, "sample.pdf", sample), writeFile(dir, "password.pdf", password))
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F2)).safeDrawingPadding()) {
        files?.let { Sample(it) }
    }
}

@Composable
private fun Sample(files: SampleFiles) {
    var demo by rememberSaveable { mutableStateOf(Demo.LocalFile) }
    var rtl by rememberSaveable { mutableStateOf(false) }
    var vertical by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    // Bytes sources compare by identity, so remember the instance.
    val bytesSource = remember(files) { PdfSource.Bytes(files.sample) }
    val invalidSource = remember { PdfSource.Bytes("This is not a PDF.".encodeToByteArray()) }
    val source =
        when (demo) {
            Demo.LocalFile -> PdfSource.File(files.samplePath)
            Demo.Bytes -> bytesSource
            Demo.Remote -> PdfSource.Remote(REMOTE_PDF, headers = mapOf("Accept" to "application/pdf"))
            Demo.NotFound -> PdfSource.File(files.samplePath + ".missing")
            Demo.Http404 -> PdfSource.Remote("https://www.w3.org/this-document-does-not-exist.pdf")
            Demo.Unreachable -> PdfSource.Remote("https://pdf.invalid/document.pdf")
            Demo.Invalid -> invalidSource
            Demo.Password -> PdfSource.File(files.passwordPath)
            Demo.TooLarge -> PdfSource.Remote(LARGE_FILE)
        }

    // Hoisted: the same state drives the inline viewer and the fullscreen viewer.
    val state = rememberPdfViewerState(source)
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        if (fullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Viewer(state, rtl, vertical, Modifier.fillMaxSize())
                Chip("Close", selected = false, Modifier.align(Alignment.TopEnd).padding(12.dp)) { fullscreen = false }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Demo.entries.forEach { entry ->
                        Chip(entry.label, selected = entry == demo) { demo = entry }
                    }
                }
                Viewer(state, rtl, vertical, Modifier.weight(1f).fillMaxWidth())
                Controls(
                    state = state,
                    rtl = rtl,
                    onRtlChange = { rtl = it },
                    vertical = vertical,
                    onVerticalChange = { vertical = it },
                    onFullscreen = { fullscreen = true },
                )
            }
        }
    }
}

@Composable
private fun Viewer(
    state: PdfViewerState,
    rtl: Boolean,
    vertical: Boolean,
    modifier: Modifier,
) {
    PdfViewer(
        state = state,
        modifier = modifier,
        orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
        pageSpacing = 8.dp,
        // Apps localize page descriptions by passing their own lambda.
        pageContentDescription =
            if (rtl) {
                { page, count -> "صفحة ${page + 1} من $count" }
            } else {
                { page, count -> "Page ${page + 1} of $count" }
            },
    )
}

@Composable
private fun Controls(
    state: PdfViewerState,
    rtl: Boolean,
    onRtlChange: (Boolean) -> Unit,
    vertical: Boolean,
    onVerticalChange: (Boolean) -> Unit,
    onFullscreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ready = state.loadState is PdfLoadState.Ready
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip("‹", selected = false) { scope.launch { state.scrollToPage(state.currentPage - 1) } }
        BasicText(
            // An LTR isolate keeps "3 / 12" from being reordered to "12 / 3" in RTL.
            text = if (ready) "\u2066${state.currentPage + 1} / ${state.pageCount}\u2069" else "–",
            style = TextStyle(fontSize = 16.sp),
        )
        Chip("›", selected = false) { scope.launch { state.scrollToPage(state.currentPage + 1) } }
        Box(Modifier.weight(1f))
        Chip("RTL", selected = rtl) { onRtlChange(!rtl) }
        Chip("Vertical", selected = vertical) { onVerticalChange(!vertical) }
        Chip("Reload", selected = false) { state.reload() }
        Chip("Fullscreen", selected = false, onClick = onFullscreen)
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    BasicText(
        text = label,
        style = TextStyle(fontSize = 14.sp, color = if (selected) Color.White else Color(0xFF202020)),
        modifier =
            modifier
                .background(if (selected) Color(0xFF3366CC) else Color.White, shape)
                .border(1.dp, Color(0xFFCCCCCC), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private const val REMOTE_PDF = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"

// A public 1 GB speed-test file: larger than the 100 MiB download limit.
private const val LARGE_FILE = "https://proof.ovh.net/files/1Gb.dat"
