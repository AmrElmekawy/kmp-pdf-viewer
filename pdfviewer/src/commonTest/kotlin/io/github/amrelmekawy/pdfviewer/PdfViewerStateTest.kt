package io.github.amrelmekawy.pdfviewer

import io.github.amrelmekawy.pdfviewer.internal.FakePdfDocument
import io.github.amrelmekawy.pdfviewer.internal.PdfDocument
import io.github.amrelmekawy.pdfviewer.internal.PdfException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfViewerStateTest {
    private class Opener(
        var next: () -> PdfDocument = { FakePdfDocument() },
    ) {
        val bypassCacheCalls = mutableListOf<Boolean>()
        var gate: CompletableDeferred<Unit>? = null

        suspend fun open(bypassCache: Boolean): PdfDocument {
            bypassCacheCalls += bypassCache
            gate?.await()
            return next()
        }
    }

    private fun TestScope.started(
        opener: Opener,
        initialPage: Int = 0,
    ): Pair<PdfViewerState, kotlinx.coroutines.Job> {
        val state = PdfViewerState(initialPage, opener::open)
        val job = launch { state.run() }
        runCurrent()
        return state to job
    }

    @Test
    fun opensTheDocument() =
        runTest {
            val opener = Opener().apply { gate = CompletableDeferred() }
            val (state, job) = started(opener)
            assertEquals(PdfLoadState.Loading, state.loadState)
            assertEquals(0, state.pageCount)

            opener.gate?.complete(Unit)
            runCurrent()
            assertEquals(PdfLoadState.Ready(3), state.loadState)
            assertEquals(3, state.pageCount)
            assertEquals(listOf(false), opener.bypassCacheCalls)
            job.cancel()
        }

    @Test
    fun mapsExpectedFailuresAndHidesUnexpectedOnes() =
        runTest {
            val (notFound, job1) = started(Opener { throw PdfException(PdfError.NotFound) })
            assertEquals(PdfLoadState.Failed(PdfError.NotFound), notFound.loadState)

            val (unknown, job2) = started(Opener { throw IllegalStateException("/private/path https://secret") })
            assertEquals(PdfLoadState.Failed(PdfError.Unknown), unknown.loadState)
            job1.cancel()
            job2.cancel()
        }

    @Test
    fun reloadAfterFailureBypassesTheCache() =
        runTest {
            var fail = true
            val opener = Opener { if (fail) throw PdfException(PdfError.Network(null)) else FakePdfDocument() }
            val (state, job) = started(opener)
            assertEquals(PdfLoadState.Failed(PdfError.Network(null)), state.loadState)

            fail = false
            state.reload()
            runCurrent()
            assertEquals(PdfLoadState.Ready(3), state.loadState)
            assertEquals(listOf(false, true), opener.bypassCacheCalls)
            job.cancel()
        }

    @Test
    fun reloadClosesThePreviousDocument() =
        runTest {
            val documents = mutableListOf<FakePdfDocument>()
            val (state, job) = started(Opener { FakePdfDocument().also { documents += it } })
            state.reload()
            runCurrent()
            assertEquals(2, documents.size)
            assertEquals(1, documents[0].closeCount)
            assertFalse(documents[1].isClosed)
            job.cancel()
        }

    @Test
    fun closesTheDocumentExactlyOnceWhenCancelled() =
        runTest {
            val document = FakePdfDocument()
            val (state, job) = started(Opener { document })
            job.cancel()
            runCurrent()
            assertEquals(1, document.closeCount)
            assertNull(state.document)
        }

    @Test
    fun closeWaitsForAnInFlightRender() =
        runTest {
            val document = FakePdfDocument().apply { renderGate = CompletableDeferred() }
            val (_, job) = started(Opener { document })
            val render = launch { document.renderPage(0, 100) }
            runCurrent()
            job.cancel()
            runCurrent()
            assertFalse(document.isClosed, "close must wait for the render")

            document.renderGate?.complete(Unit)
            runCurrent()
            assertTrue(document.isClosed)
            assertTrue(render.isCompleted)
        }

    @Test
    fun initialPageIsClampedToTheDocument() =
        runTest {
            val (state, job) = started(Opener(), initialPage = 10)
            assertEquals(2, state.currentPage)
            job.cancel()
        }

    @Test
    fun currentPageIsTheInitialPageWhileLoading() =
        runTest {
            val (state, job) = started(Opener().apply { gate = CompletableDeferred() }, initialPage = 2)
            assertEquals(2, state.currentPage)
            job.cancel()
        }

    @Test
    fun scrollToPageDoesNothingUntilReady() =
        runTest {
            val (state, job) = started(Opener().apply { gate = CompletableDeferred() })
            state.scrollToPage(2, animate = false)
            assertEquals(0, state.currentPage)
            job.cancel()
        }
}
