package io.github.amrelmekawy.pdfviewer.internal

import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RemoteCacheTest {
    @Test
    fun sha256MatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }

    @Test
    fun keyDiffersWhenAnyHeaderDiffers() {
        val base = PdfSource.Remote("https://example.com/a.pdf", mapOf("Authorization" to "Bearer one"))
        assertNotEquals(remoteCacheKey(base), remoteCacheKey(base.copy(headers = mapOf("Authorization" to "Bearer two"))))
        assertNotEquals(remoteCacheKey(base), remoteCacheKey(base.copy(headers = base.headers + ("Accept-Language" to "ar"))))
        assertNotEquals(remoteCacheKey(base), remoteCacheKey(base.copy(url = "https://example.com/b.pdf")))
    }

    @Test
    fun keyIgnoresHeaderOrderAndNameCase() {
        val a = PdfSource.Remote("https://example.com/a.pdf", mapOf("A" to "1", "b" to "2"))
        val b = PdfSource.Remote("https://example.com/a.pdf", mapOf("B" to "2", "a" to "1"))
        assertEquals(remoteCacheKey(a), remoteCacheKey(b))
    }

    @Test
    fun cacheFileNameNeverContainsTheUrlOrHeaders() {
        val source = PdfSource.Remote("https://example.com/secret.pdf", mapOf("Authorization" to "token"))
        val path = CacheDir("/cache").remoteFile(source)
        assertTrue(path.startsWith("/cache/pdfviewer/"))
        assertFalse("secret" in path || "token" in path || "example" in path)
    }

    @Test
    fun remoteToStringOmitsHeaderValues() {
        val text = PdfSource.Remote("https://example.com/a.pdf", mapOf("Authorization" to "token")).toString()
        assertTrue("Authorization" in text)
        assertFalse("token" in text)
    }

    @Test
    fun downloadSizeLimit() {
        checkDownloadSize(-1)
        checkDownloadSize(PdfRenderLimits.MAX_DOWNLOAD_BYTES)
        val error = assertFailsWith<PdfException> { checkDownloadSize(PdfRenderLimits.MAX_DOWNLOAD_BYTES + 1) }
        assertEquals(PdfError.TooLarge, error.error)
    }
}
