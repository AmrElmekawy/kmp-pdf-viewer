package io.github.amrelmekawy.pdfviewer

/**
 * Why a document failed to load.
 *
 * Errors never carry platform messages, because those can contain private paths,
 * URLs, or server details.
 */
public sealed interface PdfError {
    /** The local file does not exist or cannot be read. */
    public data object NotFound : PdfError

    /**
     * Downloading a [PdfSource.Remote] failed.
     *
     * @property httpCode the non-2xx HTTP status code, or `null` when no response arrived
     * (no connectivity, timeout, or a connection error).
     */
    public data class Network(
        val httpCode: Int?,
    ) : PdfError

    /** The data is not a valid PDF document (for example, corrupt or truncated). */
    public data object InvalidDocument : PdfError

    /** The document is encrypted. Password-protected documents are not supported. */
    public data object PasswordProtected : PdfError

    /** The download exceeded the maximum allowed size. */
    public data object TooLarge : PdfError

    /** Any other failure. */
    public data object Unknown : PdfError
}
