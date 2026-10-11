package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class IosPdfDocumentTest {
    private val cacheDir = CacheDir(NSTemporaryDirectory())

    private suspend fun open(source: PdfSource) = openPdfDocument(source, cacheDir, bypassCache = false)

    private suspend fun openBytes(data: platform.Foundation.NSData) = open(PdfSource.Bytes(data.toByteArray()))

    private suspend fun assertFailsToOpen(
        expected: PdfError,
        source: PdfSource,
    ) {
        try {
            open(source).close()
            fail("Expected $expected")
        } catch (e: PdfException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun opensBytesWithPageCountAndSizes(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.pages(200.0 to 300.0, 400.0 to 100.0))
            assertEquals(2, document.pageCount)
            assertEquals(Size(200f, 300f), document.pageSize(0))
            assertEquals(Size(400f, 100f), document.pageSize(1))
            document.close()
        }

    @Test
    fun opensAFile(): Unit =
        runBlocking {
            val document = open(PdfSource.File(PdfFixtures.writeToTempFile(PdfFixtures.multiPage(4))))
            assertEquals(4, document.pageCount)
            document.close()
        }

    @Test
    fun rotationIsAppliedToThePageSize(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.rotated())
            assertEquals(Size(300f, 200f), document.pageSize(0))
            val bitmap = document.renderPage(0, 300)
            assertEquals(300, bitmap.width)
            assertEquals(200, bitmap.height)
            document.close()
        }

    @Test
    fun rendersAtTheRequestedSizeUprightOnWhite(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.multiPage(1))
            val bitmap = document.renderPage(0, 400)
            assertEquals(400, bitmap.width)
            assertEquals(600, bitmap.height)
            val pixels = bitmap.toPixelMap()
            assertEquals(Color.Black, pixels[10, 10], "black square must be top-left")
            assertEquals(Color.White, pixels[390, 590], "background must be white")
            assertEquals(Color.White, pixels[10, 590])
            document.close()
        }

    @Test
    fun missingFileIsNotFound(): Unit = runBlocking { assertFailsToOpen(PdfError.NotFound, PdfSource.File("/does/not/exist.pdf")) }

    @Test
    fun garbageIsInvalid(): Unit =
        runBlocking { assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes("not a pdf".encodeToByteArray())) }

    @Test
    fun emptyBytesAreInvalid(): Unit = runBlocking { assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes(ByteArray(0))) }

    @Test
    fun truncatedIsInvalid(): Unit =
        runBlocking { assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes(PdfFixtures.truncated().toByteArray())) }

    @Test
    fun encryptedIsPasswordProtected(): Unit =
        runBlocking { assertFailsToOpen(PdfError.PasswordProtected, PdfSource.Bytes(PdfFixtures.encrypted().toByteArray())) }

    @Test
    fun closeTwiceIsSafeAndRenderingAfterCloseIsCancelled(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.multiPage(1))
            document.close()
            document.close()
            assertFailsWith<CancellationException> { document.renderPage(0, 100) }
        }

    @Test
    fun closeWaitsForInFlightRenders(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.multiPage(1))
            val renders = List(4) { async(Dispatchers.Default) { runCatching { document.renderPage(0, 2000) } } }
            document.close()
            // Every render either finished with a full bitmap or was refused after close; none crashed.
            renders.forEach { render ->
                val result = render.await()
                result.exceptionOrNull()?.let { assertTrue(it is CancellationException, "$it") }
                result.getOrNull()?.let { assertEquals(2000, it.width) }
            }
        }

    @Test
    fun bitmapOwnsItsPixelsAcrossLaterRenders(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.pages(200.0 to 300.0, 300.0 to 200.0))
            val first = document.renderPage(0, 200)
            val before = first.pixels()
            // Later renders allocate and free more CoreGraphics buffers, which would overwrite borrowed memory.
            repeat(20) { document.renderPage(it % 2, 200 + it) }
            assertContentEquals(before, first.pixels())
            document.close()
        }

    @Test
    fun repeatedRendersDoNotFail(): Unit =
        runBlocking {
            val document = openBytes(PdfFixtures.multiPage(3))
            repeat(60) { document.renderPage(it % 3, 1000) }
            document.close()
        }

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }
}
