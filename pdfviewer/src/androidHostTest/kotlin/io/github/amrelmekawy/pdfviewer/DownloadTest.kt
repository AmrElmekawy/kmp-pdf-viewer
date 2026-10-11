package io.github.amrelmekawy.pdfviewer

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.amrelmekawy.pdfviewer.internal.CacheDir
import io.github.amrelmekawy.pdfviewer.internal.PdfException
import io.github.amrelmekawy.pdfviewer.internal.PdfRenderLimits
import io.github.amrelmekawy.pdfviewer.internal.downloadPdf
import io.github.amrelmekawy.pdfviewer.internal.libraryDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadTest {
    private lateinit var server: HttpServer
    private lateinit var cacheDir: CacheDir
    private val requests = AtomicInteger()
    private var handler: (HttpExchange) -> Unit = { it.respond(200, BODY) }

    @BeforeTest
    fun setUp() {
        cacheDir =
            CacheDir(
                kotlin.io.path
                    .createTempDirectory("pdfviewer-test")
                    .toString(),
            )
        server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    requests.incrementAndGet()
                    try {
                        handler(exchange)
                    } catch (_: Exception) {
                        // The client went away.
                    } finally {
                        exchange.close()
                    }
                }
                executor =
                    java.util.concurrent.Executors
                        .newCachedThreadPool()
                start()
            }
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        File(cacheDir.path).deleteRecursively()
    }

    private fun source(headers: Map<String, String> = emptyMap()) =
        PdfSource.Remote("http://127.0.0.1:${server.address.port}/doc.pdf", headers)

    private fun partFiles() = File(cacheDir.libraryDir()).listFiles { f -> f.name.endsWith(".part") }.orEmpty()

    @Test
    fun downloadsSendsHeadersAndReusesTheCachedFile() =
        runBlocking {
            var authorization: String? = null
            handler = {
                authorization = it.requestHeaders.getFirst("Authorization")
                it.respond(200, BODY)
            }
            val first = downloadPdf(source(mapOf("Authorization" to "Bearer x")), cacheDir, bypassCache = false)
            assertContentEquals(BODY, first.readBytes())
            assertEquals("Bearer x", authorization)

            val second = downloadPdf(source(mapOf("Authorization" to "Bearer x")), cacheDir, bypassCache = false)
            assertEquals(first, second)
            assertEquals(1, requests.get())
        }

    @Test
    fun differentHeadersDoNotShareTheCache() =
        runBlocking {
            downloadPdf(source(mapOf("Authorization" to "a")), cacheDir, bypassCache = false)
            downloadPdf(source(mapOf("Authorization" to "b")), cacheDir, bypassCache = false)
            assertEquals(2, requests.get())
        }

    @Test
    fun bypassCacheDownloadsAgain() =
        runBlocking {
            downloadPdf(source(), cacheDir, bypassCache = false)
            handler = { it.respond(200, byteArrayOf(9, 9)) }
            val file = downloadPdf(source(), cacheDir, bypassCache = true)
            assertEquals(2, requests.get())
            assertContentEquals(byteArrayOf(9, 9), file.readBytes())
        }

    @Test
    fun httpErrorReportsTheStatusCode() =
        runBlocking {
            handler = { it.respond(404, ByteArray(0)) }
            val error = assertFailsWith<PdfException> { downloadPdf(source(), cacheDir, bypassCache = false) }
            assertEquals(PdfError.Network(404), error.error)
            assertTrue(partFiles().isEmpty())
        }

    @Test
    fun connectionFailureIsANetworkErrorWithoutCode() =
        runBlocking {
            val port = server.address.port
            server.stop(0)
            val error =
                assertFailsWith<PdfException> {
                    downloadPdf(PdfSource.Remote("http://127.0.0.1:$port/doc.pdf"), cacheDir, bypassCache = false)
                }
            assertEquals(PdfError.Network(null), error.error)
        }

    @Test
    fun nonHttpSchemeIsRejected() =
        runBlocking {
            val error =
                assertFailsWith<PdfException> {
                    downloadPdf(PdfSource.Remote("file:///etc/hosts"), cacheDir, bypassCache = false)
                }
            assertEquals(PdfError.Network(null), error.error)
        }

    @Test
    fun announcedContentLengthOverTheLimitIsTooLarge() =
        runBlocking {
            handler = {
                it.sendResponseHeaders(200, PdfRenderLimits.MAX_DOWNLOAD_BYTES + 1)
            }
            val error = assertFailsWith<PdfException> { downloadPdf(source(), cacheDir, bypassCache = false) }
            assertEquals(PdfError.TooLarge, error.error)
            assertTrue(partFiles().isEmpty())
        }

    @Test
    fun streamedBytesOverTheLimitAreTooLargeWithoutContentLength() =
        runBlocking {
            handler = {
                it.sendResponseHeaders(200, 0) // chunked: no Content-Length
                val chunk = ByteArray(1024 * 1024)
                repeat((PdfRenderLimits.MAX_DOWNLOAD_BYTES / chunk.size + 2).toInt()) { _ -> it.responseBody.write(chunk) }
            }
            val error = assertFailsWith<PdfException> { downloadPdf(source(), cacheDir, bypassCache = false) }
            assertEquals(PdfError.TooLarge, error.error)
            assertTrue(partFiles().isEmpty())
        }

    @Test
    fun cancellationRemovesThePartFile() =
        runBlocking {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            // A slow server that keeps streaming until the client goes away.
            handler = {
                it.sendResponseHeaders(200, 0)
                while (!release.await(20, TimeUnit.MILLISECONDS)) {
                    it.responseBody.write(BODY)
                    it.responseBody.flush()
                    started.countDown()
                }
            }
            val job = launch(Dispatchers.IO) { downloadPdf(source(), cacheDir, bypassCache = false) }
            started.await(10, TimeUnit.SECONDS)
            Thread.sleep(100)
            withTimeout(5_000) {
                job.cancel()
                job.join()
            }
            release.countDown()
            assertTrue(partFiles().isEmpty())
            assertTrue(File(cacheDir.libraryDir()).listFiles().orEmpty().none { f -> f.name.endsWith(".pdf") })
        }

    private companion object {
        val BODY = "%PDF-1.4 test".encodeToByteArray()

        fun HttpExchange.respond(
            code: Int,
            body: ByteArray,
        ) {
            sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) responseBody.write(body)
        }
    }
}
