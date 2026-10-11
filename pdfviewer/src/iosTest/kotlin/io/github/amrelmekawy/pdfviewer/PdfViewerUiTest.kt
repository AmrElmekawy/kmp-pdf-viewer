package io.github.amrelmekawy.pdfviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.amrelmekawy.pdfviewer.internal.PdfFixtures
import io.github.amrelmekawy.pdfviewer.internal.PdfRenderLimits
import io.github.amrelmekawy.pdfviewer.internal.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PdfViewerUiTest {
    private val threePages = PdfSource.Bytes(PdfFixtures.multiPage(3).toByteArray())

    private fun ComposeUiTest.waitForPage(description: String) =
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun showsPagesWithDefaultDescriptions(): Unit =
        runComposeUiTest {
            lateinit var state: PdfViewerState
            setContent {
                state = rememberPdfViewerState(threePages)
                PdfViewer(state, Modifier.size(300.dp, 400.dp))
            }
            waitForPage("Page 1 of 3")
            assertEquals(3, state.pageCount)
            assertEquals(0, state.currentPage)
        }

    @Test
    fun scrollToPageClampsToTheLastPage(): Unit =
        runComposeUiTest {
            lateinit var state: PdfViewerState
            lateinit var scope: CoroutineScope
            setContent {
                scope = rememberCoroutineScope()
                state = rememberPdfViewerState(threePages)
                PdfViewer(state, Modifier.size(300.dp, 400.dp))
            }
            waitForPage("Page 1 of 3")
            scope.launch { state.scrollToPage(10, animate = false) }
            waitUntil { state.currentPage == 2 }
            onNodeWithContentDescription("Page 3 of 3").assertExists()
        }

    @Test
    fun rapidPagingThroughALongDocumentStaysWithinTheMemoryBudget(): Unit =
        runComposeUiTest {
            val longDocument = PdfSource.Bytes(PdfFixtures.multiPage(200).toByteArray())
            lateinit var state: PdfViewerState
            lateinit var scope: CoroutineScope
            setContent {
                scope = rememberCoroutineScope()
                state = rememberPdfViewerState(longDocument)
                PdfViewer(state, Modifier.size(400.dp, 600.dp))
            }
            waitForPage("Page 1 of 200")
            for (page in 0 until 200 step 3) {
                scope.launch { state.scrollToPage(page, animate = false) }
                waitForIdle()
                val cache = state.bitmapCache
                assertTrue(cache.usedBytes <= PdfRenderLimits.CACHE_MAX_BYTES, "bytes ${cache.usedBytes} at page $page")
                assertTrue(cache.usedEntries <= PdfRenderLimits.CACHE_MAX_ENTRIES, "entries ${cache.usedEntries} at page $page")
            }
            waitForPage("Page 199 of 200")
        }

    @Test
    fun usesTheCustomPageDescription(): Unit =
        runComposeUiTest {
            setContent {
                PdfViewer(
                    rememberPdfViewerState(threePages),
                    Modifier.size(300.dp, 400.dp),
                    pageContentDescription = { page, count -> "صفحة ${page + 1} من $count" },
                )
            }
            waitForPage("صفحة 1 من 3")
        }

    @Test
    fun customErrorSlotReceivesTheErrorAndRetry(): Unit =
        runComposeUiTest {
            var retries = 0
            setContent {
                PdfViewer(
                    rememberPdfViewerState(PdfSource.Bytes("broken".encodeToByteArray())),
                    Modifier.fillMaxSize(),
                    error = { error, retry ->
                        BasicText(
                            text = "failed: $error",
                            modifier =
                                Modifier.clickable {
                                    retries++
                                    retry()
                                },
                        )
                    },
                )
            }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasText("failed: InvalidDocument")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("failed: InvalidDocument").performClick()
            assertEquals(1, retries)
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasText("failed: InvalidDocument")).fetchSemanticsNodes().isNotEmpty()
            }
        }

    @Test
    fun defaultErrorSlotRetryReloads(): Unit =
        runComposeUiTest {
            lateinit var state: PdfViewerState
            setContent {
                state = rememberPdfViewerState(PdfSource.File("/does/not/exist.pdf"))
                PdfViewer(state, Modifier.fillMaxSize())
            }
            waitUntil(timeoutMillis = 10_000) { state.loadState is PdfLoadState.Failed }
            onNodeWithText("The document could not be found.").assertExists()
            onNodeWithText("Retry").performClick()
            waitUntil(timeoutMillis = 10_000) { state.loadState is PdfLoadState.Failed }
            assertEquals(PdfLoadState.Failed(PdfError.NotFound), state.loadState)
        }

    @Test
    fun swipingLeftShowsTheNextPageInLeftToRight(): Unit = assertSwipeShowsNextPage(LayoutDirection.Ltr) { swipeLeft() }

    @Test
    fun swipingRightShowsTheNextPageInRightToLeft(): Unit = assertSwipeShowsNextPage(LayoutDirection.Rtl) { swipeRight() }

    @Test
    fun reverseLayoutFlipsRightToLeft(): Unit = assertSwipeShowsNextPage(LayoutDirection.Rtl, reverseLayout = true) { swipeLeft() }

    @Test
    fun swipingUpShowsTheNextPageWhenVertical(): Unit = assertSwipeShowsNextPage(LayoutDirection.Ltr, Orientation.Vertical) { swipeUp() }

    @Test
    fun verticalIgnoresTheLayoutDirection(): Unit = assertSwipeShowsNextPage(LayoutDirection.Rtl, Orientation.Vertical) { swipeUp() }

    @Test
    fun reverseLayoutPutsTheNextPageAboveWhenVertical(): Unit =
        assertSwipeShowsNextPage(LayoutDirection.Ltr, Orientation.Vertical, reverseLayout = true) { swipeDown() }

    @Test
    fun horizontalSwipesDoNotPageWhenVertical(): Unit =
        runComposeUiTest {
            lateinit var state: PdfViewerState
            setContent {
                state = rememberPdfViewerState(threePages)
                PdfViewer(state, Modifier.size(300.dp, 400.dp).testTag(VIEWER), orientation = Orientation.Vertical)
            }
            waitForPage("Page 1 of 3")
            onNodeWithTag(VIEWER).performTouchInput { swipeLeft() }
            waitForIdle()
            assertEquals(0, state.currentPage)
        }

    private fun assertSwipeShowsNextPage(
        direction: LayoutDirection,
        orientation: Orientation = Orientation.Horizontal,
        reverseLayout: Boolean = false,
        swipe: TouchInjectionScope.() -> Unit,
    ) = runComposeUiTest {
        lateinit var state: PdfViewerState
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                state = rememberPdfViewerState(threePages)
                PdfViewer(
                    state,
                    Modifier.size(300.dp, 400.dp).testTag(VIEWER),
                    orientation = orientation,
                    reverseLayout = reverseLayout,
                )
            }
        }
        waitForPage("Page 1 of 3")
        onNodeWithTag(VIEWER).performTouchInput(swipe)
        waitForIdle()
        assertEquals(1, state.currentPage)
    }

    private companion object {
        const val VIEWER = "viewer"
    }
}
