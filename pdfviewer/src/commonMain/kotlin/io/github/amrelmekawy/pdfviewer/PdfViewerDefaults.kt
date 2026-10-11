package io.github.amrelmekawy.pdfviewer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default slots and values for [PdfViewer].
 *
 * The defaults use only Compose Foundation and English text. Most apps pass their own
 * `loading` and `error` slots to match their design system and language.
 */
public object PdfViewerDefaults {
    private val ContentColor = Color(0xFF757575)

    /** A small spinning indicator, centered. */
    @Composable
    public fun Loading() {
        val transition = rememberInfiniteTransition()
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing), RepeatMode.Restart),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = "Loading"
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
            ) {
                drawArc(
                    color = ContentColor,
                    startAngle = rotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }

    /** A short English description of [error] with a "Retry" button that calls [retry]. */
    @Composable
    public fun Error(
        error: PdfError,
        retry: () -> Unit,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = message(error),
                style = TextStyle(color = ContentColor, fontSize = 16.sp, textAlign = TextAlign.Center),
            )
            BasicText(
                text = "Retry",
                style = TextStyle(color = ContentColor, fontSize = 16.sp),
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = retry)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    /** "Page X of Y", 1-based, for the 0-based [page]. */
    public fun pageContentDescription(
        page: Int,
        pageCount: Int,
    ): String = "Page ${page + 1} of $pageCount"

    private fun message(error: PdfError): String =
        when (error) {
            PdfError.NotFound -> "The document could not be found."
            is PdfError.Network -> "The document could not be downloaded."
            PdfError.InvalidDocument -> "The document is damaged or not a PDF."
            PdfError.PasswordProtected -> "Password-protected documents are not supported."
            PdfError.TooLarge -> "The document is too large to download."
            PdfError.Unknown -> "The document could not be opened."
        }
}
