package io.github.amrelmekawy.pdfviewer

/** The loading progress of the document held by a [PdfViewerState]. */
public sealed interface PdfLoadState {
    /** The document is being downloaded or opened. */
    public data object Loading : PdfLoadState

    /** The document is open and has [pageCount] pages. */
    public data class Ready(
        val pageCount: Int,
    ) : PdfLoadState

    /** The document could not be opened. Call [PdfViewerState.reload] to try again. */
    public data class Failed(
        val error: PdfError,
    ) : PdfLoadState
}
