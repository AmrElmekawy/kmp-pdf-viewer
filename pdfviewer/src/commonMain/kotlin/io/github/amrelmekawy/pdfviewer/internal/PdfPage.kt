package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One page, fitted into the available space, zoomable, rendered through [cache].
 *
 * Container-agnostic: it fills whatever space it gets, so a future vertical list can reuse it.
 * The render is cancelled when the page leaves the composition.
 *
 * @param isProtected whether a page's bitmap must not be evicted (the current page).
 */
@Composable
internal fun PdfPage(
    document: PdfDocument,
    cache: PageBitmapCache,
    index: Int,
    isCurrent: Boolean,
    isProtected: (page: Int) -> Boolean,
    contentDescription: String,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val pageSize = document.pageSize(index)
        val fitted = fit(pageSize, viewport)
        var settledScale by remember { mutableFloatStateOf(1f) }
        val renderSize = renderSizeFor(pageSize, (fitted.width * settledScale).roundToInt())

        if (fitted.width >= 1f && fitted.height >= 1f) {
            LaunchedEffect(document, index, renderSize, isCurrent) {
                try {
                    cache.load(index, renderSize, prefetch = !isCurrent, isProtected = isProtected) {
                        document.renderPage(index, renderSize.widthPx)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A page that fails to render stays blank; the document itself is still usable.
                }
            }
        }

        ZoomableBox(
            bounds = ZoomBounds(viewport.width, viewport.height, fitted.width, fitted.height),
            isCurrent = isCurrent,
            onZoomedChange = onZoomedChange,
            onZoomSettled = { settledScale = it },
            modifier = Modifier.fillMaxSize(),
        ) {
            val fittedDp = with(LocalDensity.current) { fitted.toDpSize() }
            Box(
                Modifier
                    .size(fittedDp)
                    .background(Color.White)
                    .semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    },
            ) {
                val bitmap = cache.bitmap(index)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
    }
}

/** The largest size with the aspect ratio of [page] that fits in [viewport]. */
internal fun fit(
    page: Size,
    viewport: Size,
): Size {
    if (page.width <= 0f || page.height <= 0f) return Size.Zero
    val scale = min(viewport.width / page.width, viewport.height / page.height)
    return Size(page.width * scale, page.height * scale)
}
