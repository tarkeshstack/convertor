package com.tarkeshstack.speakeasy.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Soft saffron-white-green wash evoking the Indian tricolor — pastel, not the literal
 *  flag colors, so body text stays easy to read on top of it. */
val TricolorBackground: Brush
    get() = Brush.verticalGradient(
        colors = listOf(IndiaSaffronSoft, Color.White, IndiaGreenSoft),
    )

/** A faint Ashoka Chakra (24-spoke wheel) as a background watermark — the detail that
 *  makes the tricolor read as "Indian flag" rather than just a warm gradient. */
@Composable
fun AshokaChakraWatermark(modifier: Modifier = Modifier, alpha: Float = 0.05f) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = IndiaChakraBlue,
            radius = radius,
            center = center,
            alpha = alpha,
            style = Stroke(width = radius * 0.035f),
        )
        drawCircle(color = IndiaChakraBlue, radius = radius * 0.04f, center = center, alpha = alpha)
        for (spoke in 0 until 24) {
            val angle = Math.toRadians((spoke * 15).toDouble())
            val end = Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat(),
            )
            drawLine(color = IndiaChakraBlue, start = center, end = end, alpha = alpha, strokeWidth = radius * 0.018f)
        }
    }
}
