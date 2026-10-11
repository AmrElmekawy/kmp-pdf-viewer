package io.github.amrelmekawy.pdfviewer.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenOnTest {
    @Test
    fun documentOpenedAfterCancellationIsClosed() =
        runTest {
            val document = FakePdfDocument()
            val opening = CompletableDeferred<Unit>()
            val job =
                launch {
                    openOn(StandardTestDispatcher(testScheduler)) {
                        // Like a blocking native open, this does not observe cancellation.
                        withContext(NonCancellable) { opening.await() }
                        document
                    }
                }
            runCurrent()
            job.cancel()
            opening.complete(Unit)
            runCurrent()
            assertEquals(1, document.closeCount)
        }
}
