@file:OptIn(ExperimentalForeignApi::class)

package io.github.amrelmekawy.pdfviewer.internal

import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.setValue
import platform.Foundation.writeData
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads [source] into the cache and returns the complete file's path. Reuses a cached
 * file unless [bypassCache]. Streams into a `.part` file that is moved only on success.
 */
internal suspend fun downloadPdf(
    source: PdfSource.Remote,
    cacheDir: CacheDir,
    bypassCache: Boolean,
): String {
    val files = NSFileManager.defaultManager
    val target = cacheDir.remoteFile(source)
    if (bypassCache) {
        files.removeItemAtPath(target, null)
    } else if (files.fileExistsAtPath(target)) {
        return target
    }
    files.createDirectoryAtPath(cacheDir.libraryDir(), withIntermediateDirectories = true, attributes = null, error = null)

    val part = "$target.${NSUUID().UUIDString}.part"
    try {
        val url = NSURL.URLWithString(source.url)
        val scheme = url?.scheme?.lowercase()
        if (url == null || (scheme != "https" && scheme != "http")) throw PdfException(PdfError.Network(null))
        val request =
            NSMutableURLRequest.requestWithURL(url).apply {
                source.headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
            }
        if (!files.createFileAtPath(part, contents = null, attributes = null)) throw PdfException(PdfError.Unknown)
        val output = NSFileHandle.fileHandleForWritingAtPath(part) ?: throw PdfException(PdfError.Unknown)
        try {
            stream(request, output)
        } finally {
            output.closeFile()
        }
        files.removeItemAtPath(target, null)
        if (!files.moveItemAtPath(part, toPath = target, error = null)) throw PdfException(PdfError.Unknown)
        return target
    } finally {
        files.removeItemAtPath(part, null)
    }
}

private suspend fun stream(
    request: NSMutableURLRequest,
    output: NSFileHandle,
) = suspendCancellableCoroutine { continuation ->
    // Ephemeral: no shared cookies or URL cache, so credentials stay with their own source.
    val configuration =
        NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
            // NSURLSession has no separate connect timeout; this is the idle timeout.
            timeoutIntervalForRequest = PdfRenderLimits.READ_TIMEOUT_MILLIS / 1000.0
            URLCache = null
        }
    val delegate =
        StreamingDelegate(output) { failure ->
            if (failure == null) continuation.resume(Unit) else continuation.resumeWithException(failure)
        }
    val session = NSURLSession.sessionWithConfiguration(configuration, delegate, delegateQueue = null)
    val task = session.dataTaskWithRequest(request)
    continuation.invokeOnCancellation { session.invalidateAndCancel() }
    task.resume()
}

/** Writes the response body to [output], enforcing the status code and the size limit. */
private class StreamingDelegate(
    private val output: NSFileHandle,
    private val onComplete: (failure: Throwable?) -> Unit,
) : NSObject(),
    NSURLSessionDataDelegateProtocol {
    private var failure: PdfException? = null
    private var received = 0L

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        val code = (didReceiveResponse as? NSHTTPURLResponse)?.statusCode?.toInt()
        failure =
            when {
                code == null -> PdfException(PdfError.Network(null))
                code !in 200..299 -> PdfException(PdfError.Network(code))
                didReceiveResponse.expectedContentLength > PdfRenderLimits.MAX_DOWNLOAD_BYTES -> PdfException(PdfError.TooLarge)
                else -> null
            }
        completionHandler(if (failure == null) NSURLSessionResponseAllow else NSURLSessionResponseCancel)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        received += didReceiveData.length.toLong()
        failure =
            when {
                received > PdfRenderLimits.MAX_DOWNLOAD_BYTES -> PdfException(PdfError.TooLarge)
                !output.writeData(didReceiveData, error = null) -> PdfException(PdfError.Unknown)
                else -> return
            }
        dataTask.cancel()
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        session.finishTasksAndInvalidate()
        onComplete(failure ?: didCompleteWithError?.let { PdfException(PdfError.Network(null)) })
    }
}
