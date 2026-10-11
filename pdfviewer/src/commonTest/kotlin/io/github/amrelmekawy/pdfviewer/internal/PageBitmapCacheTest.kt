package io.github.amrelmekawy.pdfviewer.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageBitmapCacheTest {
    // 10x10 RGBA = 400 bytes per entry.
    private val small = RenderSize(10, 10)

    private fun bitmap(size: RenderSize) = FakeImageBitmap(size.widthPx, size.heightPx)

    @Test
    fun cachedPageIsNotRenderedAgain() =
        runTest {
            val cache = PageBitmapCache()
            var renders = 0
            repeat(3) {
                cache.load(0, small, prefetch = false, isProtected = { false }) {
                    renders++
                    bitmap(small)
                }
            }
            assertEquals(1, renders)
            assertNotNull(cache.bitmap(0))
        }

    @Test
    fun evictsLeastRecentlyUsedBeyondMaxEntries() =
        runTest {
            val cache = PageBitmapCache(maxEntries = 2, maxBytes = Long.MAX_VALUE)
            for (page in 0..2) cache.load(page, small, prefetch = false, isProtected = { false }) { bitmap(small) }
            assertNull(cache.bitmap(0))
            assertNotNull(cache.bitmap(1))
            assertNotNull(cache.bitmap(2))
        }

    @Test
    fun evictsBeforeRenderingSoBothLimitsHoldDuringTheRender() =
        runTest {
            val cache = PageBitmapCache(maxEntries = 5, maxBytes = 1000)
            for (page in 0 until 20) {
                cache.load(page, small, prefetch = false, isProtected = { false }) {
                    // The reservation for this render is already counted.
                    assertTrue(cache.usedBytes <= 1000, "bytes ${cache.usedBytes}")
                    assertTrue(cache.usedEntries <= 5, "entries ${cache.usedEntries}")
                    bitmap(small)
                }
            }
            assertTrue(cache.usedBytes <= 1000)
        }

    @Test
    fun randomAccessStaysUnderBothLimits() =
        runTest {
            val maxBytes = 64L * 1024
            val cache = PageBitmapCache(maxEntries = 5, maxBytes = maxBytes)
            val random = Random(42)
            var current = 0
            repeat(500) {
                current = (current + random.nextInt(-1, 2)).coerceIn(0, 199)
                val page = (current + random.nextInt(-1, 2)).coerceIn(0, 199)
                val size = RenderSize(random.nextInt(10, 90), random.nextInt(10, 90))
                cache.load(page, size, prefetch = page != current, isProtected = { it == current }) {
                    assertTrue(cache.usedBytes <= maxBytes)
                    assertTrue(cache.usedEntries <= 5)
                    bitmap(size)
                }
                assertTrue(cache.usedBytes <= maxBytes)
                assertTrue(cache.usedEntries <= 5)
            }
        }

    @Test
    fun protectedPageIsNeverEvicted() =
        runTest {
            val cache = PageBitmapCache(maxEntries = 2, maxBytes = Long.MAX_VALUE)
            cache.load(0, small, prefetch = false, isProtected = { it == 0 }) { bitmap(small) }
            for (page in 1..5) cache.load(page, small, prefetch = false, isProtected = { it == 0 }) { bitmap(small) }
            assertNotNull(cache.bitmap(0))
        }

    @Test
    fun prefetchIsSkippedWhenItWouldEvictTheCurrentPage() =
        runTest {
            val cache = PageBitmapCache(maxEntries = 5, maxBytes = 600)
            cache.load(0, small, prefetch = false, isProtected = { it == 0 }) { bitmap(small) }
            var rendered = false
            cache.load(1, small, prefetch = true, isProtected = { it == 0 }) {
                rendered = true
                bitmap(small)
            }
            assertTrue(!rendered)
            assertNull(cache.bitmap(1))
            assertNotNull(cache.bitmap(0))
        }

    @Test
    fun newWidthReplacesThePageAndKeepsTheOldBitmapUntilReady() =
        runTest {
            val cache = PageBitmapCache()
            cache.load(0, small, prefetch = false, isProtected = { false }) { bitmap(small) }
            val gate = CompletableDeferred<Unit>()
            val large = RenderSize(20, 20)
            val job =
                launch {
                    cache.load(0, large, prefetch = false, isProtected = { false }) {
                        gate.await()
                        bitmap(large)
                    }
                }
            runCurrent()
            assertEquals(10, cache.bitmap(0)?.width)
            gate.complete(Unit)
            job.join()
            assertEquals(20, cache.bitmap(0)?.width)
            assertEquals(large.byteCount, cache.usedBytes)
        }

    @Test
    fun cancelledRenderReleasesItsReservation() =
        runTest {
            val cache = PageBitmapCache()
            val job =
                launch {
                    cache.load(0, small, prefetch = false, isProtected = { false }) {
                        CompletableDeferred<Unit>().await()
                        bitmap(small)
                    }
                }
            runCurrent()
            assertEquals(small.byteCount, cache.usedBytes)
            job.cancel()
            runCurrent()
            assertEquals(0, cache.usedBytes)
            assertEquals(0, cache.usedEntries)
        }
}
