package io.github.amrelmekawy.pdfviewer.internal

import io.github.amrelmekawy.pdfviewer.PdfError

/** An expected failure, mapped to a safe public [error]. The [cause] is kept for debugging only. */
internal class PdfException(
    val error: PdfError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

internal fun Throwable.toPdfError(): PdfError = (this as? PdfException)?.error ?: PdfError.Unknown
