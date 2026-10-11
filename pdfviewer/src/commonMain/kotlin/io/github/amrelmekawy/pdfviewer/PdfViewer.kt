package io.github.amrelmekawy.pdfviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.amrelmekawy.pdfviewer.internal.PdfPage

/**
 * Shows the document held by [state] as horizontally swipeable pages with pinch zoom,
 * pan while zoomed, and double-tap zoom/reset.
 *
 * Pages follow the layout direction, so in a right-to-left layout the next page is on the
 * left. Page content is never mirrored.
 *
 * ```
 * val state = rememberPdfViewerState(PdfSource.Remote("https://example.com/file.pdf"))
 * PdfViewer(
 *     state = state,
 *     modifier = Modifier.fillMaxSize(),
 *     pageContentDescription = { page, count -> "صفحة ${page + 1} من $count" },
 * )
 * ```
 *
 * @param state the state that owns the document; see [rememberPdfViewerState].
 * @param modifier the modifier for the viewer.
 * @param reverseLayout whether to reverse the page order relative to the layout direction.
 * @param pageSpacing the space between pages while swiping.
 * @param pageContentDescription the accessibility description of a page, given its 0-based
 * index and the page count. Pass your own to localize it.
 * @param loading shown while the document loads.
 * @param error shown when the document fails to load; call `retry` to load it again.
 */
@Composable
public fun PdfViewer(
    state: PdfViewerState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    pageSpacing: Dp = 0.dp,
    pageContentDescription: (page: Int, pageCount: Int) -> String = PdfViewerDefaults::pageContentDescription,
    loading: @Composable () -> Unit = { PdfViewerDefaults.Loading() },
    error: @Composable (error: PdfError, retry: () -> Unit) -> Unit = { e, retry -> PdfViewerDefaults.Error(e, retry) },
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val loadState = state.loadState) {
            PdfLoadState.Loading -> loading()
            is PdfLoadState.Failed -> error(loadState.error, state::reload)
            is PdfLoadState.Ready -> {
                val pagerState = state.pagerState ?: return@Box
                val document = state.document ?: return@Box
                val cache = state.bitmapCache
                var isCurrentPageZoomed by remember(pagerState) { mutableStateOf(false) }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = pageSpacing,
                    beyondViewportPageCount = 1,
                    reverseLayout = reverseLayout,
                    userScrollEnabled = !isCurrentPageZoomed,
                ) { index ->
                    PdfPage(
                        document = document,
                        cache = cache,
                        index = index,
                        isCurrent = index == pagerState.currentPage,
                        isProtected = { it == pagerState.currentPage },
                        contentDescription = pageContentDescription(index, loadState.pageCount),
                        onZoomedChange = { isCurrentPageZoomed = it },
                    )
                }
            }
        }
    }
}
