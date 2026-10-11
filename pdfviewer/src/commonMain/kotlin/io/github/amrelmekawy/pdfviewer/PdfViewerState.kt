package io.github.amrelmekawy.pdfviewer

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.amrelmekawy.pdfviewer.internal.PageBitmapCache
import io.github.amrelmekawy.pdfviewer.internal.PdfDocument
import io.github.amrelmekawy.pdfviewer.internal.openPdfDocument
import io.github.amrelmekawy.pdfviewer.internal.rememberCacheDir
import io.github.amrelmekawy.pdfviewer.internal.toPdfError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/**
 * The state of a [PdfViewer]: the open document, its load progress, and the current page.
 *
 * Create it with [rememberPdfViewerState] and hoist it to observe or control the viewer, for
 * example to show a page indicator or to reuse the same state in a fullscreen screen.
 * The state owns the native document and closes it when it leaves the composition.
 */
@Stable
public class PdfViewerState internal constructor(
    initialPage: Int,
    private val open: suspend (bypassCache: Boolean) -> PdfDocument,
) {
    /** The load progress of the document. */
    public var loadState: PdfLoadState by mutableStateOf(PdfLoadState.Loading)
        private set

    /** The number of pages, or 0 until [loadState] is [PdfLoadState.Ready]. */
    public val pageCount: Int
        get() = (loadState as? PdfLoadState.Ready)?.pageCount ?: 0

    /** The 0-based index of the page currently shown. */
    public val currentPage: Int
        get() = pagerState?.currentPage ?: pendingPage

    /**
     * Shows [page], clamped to `0 until pageCount`. Does nothing until [loadState] is
     * [PdfLoadState.Ready].
     *
     * @param animate whether to scroll to the page with an animation.
     */
    public suspend fun scrollToPage(
        page: Int,
        animate: Boolean = true,
    ) {
        val pager = pagerState ?: return
        val target = page.coerceIn(0, pageCount - 1)
        if (animate) pager.animateScrollToPage(target) else pager.scrollToPage(target)
    }

    /**
     * Opens the document again, for example after a [PdfLoadState.Failed].
     *
     * A [PdfSource.Remote] is downloaded again even if a cached copy exists.
     * The current page is kept when possible.
     */
    public fun reload() {
        reloadRequests.trySend(Unit)
    }

    // Created only once the page count is known, so the pager never clamps the initial page to 0.
    internal var pagerState: PagerState? by mutableStateOf(null)
        private set

    internal var document: PdfDocument? by mutableStateOf(null)
        private set

    internal var bitmapCache: PageBitmapCache = PageBitmapCache()
        private set

    private var pendingPage = initialPage.coerceAtLeast(0)
    private val reloadRequests = Channel<Unit>(Channel.CONFLATED)

    /** Opens the document and serves reload requests until cancelled, then closes the document. */
    internal suspend fun run() {
        try {
            var bypassCache = false
            while (true) {
                load(bypassCache)
                reloadRequests.receive()
                bypassCache = true
            }
        } finally {
            withContext(NonCancellable) { closeDocument() }
        }
    }

    private suspend fun load(bypassCache: Boolean) {
        pendingPage = currentPage
        closeDocument()
        loadState = PdfLoadState.Loading
        try {
            val opened = open(bypassCache)
            document = opened
            bitmapCache = PageBitmapCache()
            pagerState =
                PagerState(currentPage = pendingPage.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))) {
                    opened.pageCount
                }
            loadState = PdfLoadState.Ready(opened.pageCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadState = PdfLoadState.Failed(e.toPdfError())
        }
    }

    private suspend fun closeDocument() {
        val closing = document ?: return
        document = null
        pagerState = null
        bitmapCache = PageBitmapCache()
        closing.close()
    }

    internal companion object {
        fun saver(open: suspend (bypassCache: Boolean) -> PdfDocument): Saver<PdfViewerState, Int> =
            Saver(
                save = { it.currentPage },
                restore = { PdfViewerState(initialPage = it, open = open) },
            )
    }
}

/**
 * Creates and remembers a [PdfViewerState] that shows [source], starting at [initialPage].
 *
 * The current page survives configuration changes. When [source] changes, the old document
 * is closed and the new one is opened at [initialPage]. The document is closed when the
 * state leaves the composition.
 *
 * ```
 * val state = rememberPdfViewerState(PdfSource.File(path))
 * PdfViewer(state, Modifier.fillMaxSize())
 * ```
 */
@Composable
public fun rememberPdfViewerState(
    source: PdfSource,
    initialPage: Int = 0,
): PdfViewerState {
    val cacheDir = rememberCacheDir()
    val open: suspend (Boolean) -> PdfDocument = { bypassCache -> openPdfDocument(source, cacheDir, bypassCache) }
    val state =
        rememberSaveable(source, cacheDir, saver = PdfViewerState.saver(open)) {
            PdfViewerState(initialPage = initialPage, open = open)
        }
    LaunchedEffect(state) { state.run() }
    return state
}
