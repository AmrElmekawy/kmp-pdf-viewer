package io.github.amrelmekawy.pdfviewer.internal

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import android.graphics.pdf.PdfDocument as AndroidPdfWriter

@RunWith(AndroidJUnit4::class)
class AndroidPdfDocumentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cacheDir = CacheDir(context.cacheDir.absolutePath)

    /** Pages of the given sizes in points, each with a black square in its top-left corner. */
    private fun pdf(vararg sizes: Pair<Int, Int>): ByteArray {
        val writer = AndroidPdfWriter()
        sizes.forEachIndexed { index, (width, height) ->
            val page = writer.startPage(AndroidPdfWriter.PageInfo.Builder(width, height, index + 1).create())
            page.canvas.drawRect(0f, 0f, width / 4f, height / 4f, Paint().apply { color = Color.BLACK })
            writer.finishPage(page)
        }
        return ByteArrayOutputStream().also { writer.writeTo(it) }.toByteArray().also { writer.close() }
    }

    private suspend fun open(source: PdfSource) = openPdfDocument(source, cacheDir, bypassCache = false)

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

    private fun libraryFiles() = File(cacheDir.libraryDir()).listFiles().orEmpty().toList()

    @Test
    fun opensBytesWithPageCountAndSizes() =
        runBlocking {
            val document = open(PdfSource.Bytes(pdf(200 to 300, 400 to 100)))
            assertEquals(2, document.pageCount)
            assertEquals(Size(200f, 300f), document.pageSize(0))
            assertEquals(Size(400f, 100f), document.pageSize(1))
            document.close()
        }

    @Test
    fun opensAFileWithoutTouchingIt() =
        runBlocking {
            val file = File(context.cacheDir, "fixture.pdf").apply { writeBytes(pdf(200 to 300, 200 to 300)) }
            val document = open(PdfSource.File(file.absolutePath))
            assertEquals(2, document.pageCount)
            document.close()
            assertTrue(file.exists(), "the caller's file must not be deleted")
        }

    @Test
    fun bytesTempFileIsDeletedOnClose() =
        runBlocking {
            val before = libraryFiles().size
            val document = open(PdfSource.Bytes(pdf(200 to 300)))
            assertEquals(before + 1, libraryFiles().size)
            document.close()
            assertEquals(before, libraryFiles().size)
        }

    @Test
    fun rendersAtTheRequestedSizeUprightOnWhite() =
        runBlocking {
            val document = open(PdfSource.Bytes(pdf(200 to 300)))
            val bitmap = document.renderPage(0, 400).asAndroidBitmap()
            assertEquals(400, bitmap.width)
            assertEquals(600, bitmap.height)
            assertEquals(Color.BLACK, bitmap.getPixel(10, 10))
            assertEquals(Color.WHITE, bitmap.getPixel(390, 590))
            document.close()
        }

    @Test
    fun missingFileIsNotFound() = runBlocking { assertFailsToOpen(PdfError.NotFound, PdfSource.File("/does/not/exist.pdf")) }

    @Test
    fun garbageIsInvalid() = runBlocking { assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes("not a pdf".encodeToByteArray())) }

    @Test
    fun truncatedIsInvalid() =
        runBlocking {
            val full = pdf(200 to 300, 200 to 300, 200 to 300)
            assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes(full.copyOf(full.size / 3)))
        }

    @Test
    fun failedOpenLeavesNoTempFile() =
        runBlocking {
            val before = libraryFiles().size
            assertFailsToOpen(PdfError.InvalidDocument, PdfSource.Bytes("not a pdf".encodeToByteArray()))
            assertEquals(before, libraryFiles().size)
        }

    @Test
    fun closeTwiceIsSafeAndRenderingAfterCloseIsCancelled() =
        runBlocking {
            val document = open(PdfSource.Bytes(pdf(200 to 300)))
            document.close()
            document.close()
            assertFailsWith<CancellationException> { document.renderPage(0, 100) }
            Unit
        }

    @Test
    fun closeWaitsForInFlightRenders() =
        runBlocking {
            val document = open(PdfSource.Bytes(pdf(200 to 300)))
            val renders = List(4) { async(Dispatchers.Default) { runCatching { document.renderPage(0, 2000) } } }
            document.close()
            renders.forEach { render ->
                val result = render.await()
                result.exceptionOrNull()?.let { assertTrue(it is CancellationException, "$it") }
                result.getOrNull()?.let { assertEquals(2000, it.width) }
            }
        }

    @Test
    fun concurrentRendersAreSerialized() =
        runBlocking {
            // PdfRenderer throws if two pages are open at once; the lock must prevent that.
            val document = open(PdfSource.Bytes(pdf(200 to 300, 300 to 200, 200 to 300)))
            List(30) { async(Dispatchers.Default) { document.renderPage(it % 3, 500) } }.forEach { it.await() }
            document.close()
        }
}
