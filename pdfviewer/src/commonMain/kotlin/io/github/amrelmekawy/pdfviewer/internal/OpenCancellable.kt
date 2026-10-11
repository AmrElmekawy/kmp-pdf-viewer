package io.github.amrelmekawy.pdfviewer.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs [open] on [dispatcher] and returns the document, closing it if the caller is
 * cancelled after it opened (`withContext` throws on return when cancelled meanwhile).
 */
internal suspend fun openOn(
    dispatcher: CoroutineDispatcher,
    open: suspend () -> PdfDocument,
): PdfDocument {
    var opened: PdfDocument? = null
    try {
        return withContext(dispatcher) { open().also { opened = it } }
    } catch (e: CancellationException) {
        withContext(NonCancellable) { opened?.close() }
        throw e
    }
}
