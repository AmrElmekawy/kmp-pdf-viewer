package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A least-recently-used cache of rendered pages, bounded by both entry count and bytes.
 *
 * Holds at most one bitmap per page: a new render of a page at another width replaces the
 * old one, which stays visible as a placeholder until then. Room is reserved (by evicting)
 * *before* a render starts, so the cache never goes over budget, not even briefly.
 *
 * Not thread-safe: use it from the main thread only. Rendering itself may run elsewhere.
 */
internal class PageBitmapCache(
    private val maxEntries: Int = PdfRenderLimits.CACHE_MAX_ENTRIES,
    private val maxBytes: Long = PdfRenderLimits.CACHE_MAX_BYTES,
) {
    private class Entry(
        val widthPx: Int,
        val byteCount: Long,
        val bitmap: ImageBitmap,
    )

    // Iteration order is least recently used first.
    private val entries = LinkedHashMap<Int, Entry>()
    private var reservedBytes = 0L
    private var reservedEntries = 0

    // Lets composables observe [bitmap] although [entries] is not snapshot state.
    private val version = mutableIntStateOf(0)

    /** Bytes held by cached bitmaps plus renders in flight. */
    val usedBytes: Long get() = entries.values.sumOf { it.byteCount } + reservedBytes

    /** Cached bitmaps plus renders in flight that will add an entry. */
    val usedEntries: Int get() = entries.size + reservedEntries

    /** The latest bitmap of [page] at any width, or `null`. Observable from composition. */
    fun bitmap(page: Int): ImageBitmap? {
        version.intValue
        return entries[page]?.bitmap
    }

    /**
     * Makes sure [page] is cached at [size], rendering it with [render] if needed.
     *
     * Entries for pages where [isProtected] returns `true` are never evicted. A [prefetch]
     * is skipped if it does not fit after evicting unprotected pages; a non-prefetch render
     * always proceeds.
     */
    suspend fun load(
        page: Int,
        size: RenderSize,
        prefetch: Boolean,
        isProtected: (page: Int) -> Boolean,
        render: suspend () -> ImageBitmap,
    ) {
        val cached = entries[page]
        if (cached != null && cached.widthPx == size.widthPx) {
            entries.remove(page)
            entries[page] = cached
            return
        }

        val addsEntry = if (cached == null) 1 else 0
        if (!reserve(page, size.byteCount, addsEntry, prefetch, isProtected)) return

        val bitmap =
            try {
                render()
            } finally {
                reservedBytes -= size.byteCount
                reservedEntries -= addsEntry
            }
        entries.remove(page)
        entries[page] = Entry(size.widthPx, size.byteCount, bitmap)
        version.intValue++
    }

    private fun reserve(
        page: Int,
        byteCount: Long,
        addsEntry: Int,
        prefetch: Boolean,
        isProtected: (page: Int) -> Boolean,
    ): Boolean {
        // The page's own older bitmap stays as a placeholder until its replacement is ready.
        val evictable = entries.filterKeys { it != page && !isProtected(it) }

        fun fits(
            evictedBytes: Long,
            evictedEntries: Int,
        ) = usedBytes - evictedBytes + byteCount <= maxBytes &&
            usedEntries - evictedEntries + addsEntry <= maxEntries

        if (prefetch && !fits(evictable.values.sumOf { it.byteCount }, evictable.size)) return false

        for ((evictedPage, _) in evictable) {
            if (fits(0, 0)) break
            entries.remove(evictedPage)
            version.intValue++
        }
        reservedBytes += byteCount
        reservedEntries += addsEntry
        return true
    }
}
