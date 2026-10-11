@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.amrelmekawy.pdfviewer.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.subdataWithRange
import platform.Foundation.writeToFile
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFDocumentOwnerPasswordOption
import platform.PDFKit.PDFDocumentUserPasswordOption
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.posix.memcpy

/** PDFs generated at test time with UIKit, so no fixture files are committed. */
internal object PdfFixtures {
    /** Pages of the given sizes in points, each with a black square in its top-left corner. */
    fun pages(vararg sizes: Pair<Double, Double>): NSData {
        val (firstWidth, firstHeight) = sizes.first()
        val renderer = UIGraphicsPDFRenderer(CGRectMake(0.0, 0.0, firstWidth, firstHeight), UIGraphicsPDFRendererFormat())
        return renderer.PDFDataWithActions { context ->
            for ((width, height) in sizes) {
                context?.beginPageWithBounds(CGRectMake(0.0, 0.0, width, height), pageInfo = emptyMap<Any?, Any?>())
                val cg = context?.CGContext
                CGContextSetRGBFillColor(cg, 0.0, 0.0, 0.0, 1.0)
                CGContextFillRect(cg, CGRectMake(0.0, 0.0, width / 4, height / 4))
            }
        }
    }

    fun multiPage(count: Int = 3): NSData = pages(*Array(count) { 200.0 to 300.0 })

    /** One portrait page with a 90° /Rotate, so it displays as landscape. */
    fun rotated(): NSData {
        val document = PDFDocument(data = pages(200.0 to 300.0))
        document.pageAtIndex(0u)?.rotation = 90
        return checkNotNull(document.dataRepresentation())
    }

    fun encrypted(): NSData {
        val document = PDFDocument(data = multiPage(1))
        val options = mapOf<Any?, Any?>(PDFDocumentUserPasswordOption to "secret", PDFDocumentOwnerPasswordOption to "secret")
        return checkNotNull(document.dataRepresentationWithOptions(options))
    }

    fun truncated(): NSData {
        val full = multiPage(3)
        return full.subdataWithRange(NSMakeRange(0u, full.length / 3u))
    }

    fun writeToTempFile(data: NSData): String {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".pdf"
        check(data.writeToFile(path, atomically = true))
        return path
    }
}

internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
