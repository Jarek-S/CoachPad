package pl.net.orlik.presentation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Draws a circular track along the element's edge and, on top of it, an arc filled
 * clockwise from 12 o'clock to [progress] (0..1). Progress is read at draw time,
 * so animating it redraws without recomposition.
 */
fun Modifier.ring(
    width: Dp,
    trackColor: Color,
    progressColor: Color = Color.Transparent,
    progress: () -> Float = { 0f },
): Modifier = drawBehind {
    val strokePx = width.toPx()
    val inset = strokePx / 2
    val arcSize = Size(size.width - strokePx, size.height - strokePx)
    val topLeft = Offset(inset, inset)
    drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokePx))
    val sweep = 360f * progress().coerceIn(0f, 1f)
    if (sweep > 0f) {
        drawArc(
            progressColor, -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round),
        )
    }
}
