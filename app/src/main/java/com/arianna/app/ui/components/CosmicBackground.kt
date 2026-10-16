package com.arianna.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.arianna.app.ui.theme.Palette
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** Fondo de la app: degradado nocturno, estrellas y dos capas de montañas. */
@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Semilla fija: las estrellas no "saltan" entre recomposiciones.
    val stars = remember {
        val random = Random(7)
        List(46) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * 0.75f,
                radius = 1.2f + random.nextFloat() * 2.2f,
                alpha = 0.25f + random.nextFloat() * 0.55f,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Palette.NightTop, Palette.NightMid, Palette.NightBottom),
                    ),
                )
                stars.forEach { star ->
                    drawCircle(
                        color = Palette.Star.copy(alpha = star.alpha),
                        radius = star.radius * density / 2f,
                        center = Offset(star.x * size.width, star.y * size.height),
                    )
                }
                drawMountains()
            },
        content = content,
    )
}

private fun DrawScope.drawMountains() {
    val w = size.width
    val h = size.height

    val far = Path().apply {
        moveTo(0f, h * 0.42f)
        lineTo(w * 0.18f, h * 0.30f)
        lineTo(w * 0.34f, h * 0.40f)
        lineTo(w * 0.55f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.38f)
        lineTo(w, h * 0.28f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(far, Brush.verticalGradient(listOf(Palette.MountainFar, Palette.NightBottom), startY = h * 0.2f))

    val near = Path().apply {
        moveTo(0f, h * 0.62f)
        lineTo(w * 0.22f, h * 0.48f)
        lineTo(w * 0.42f, h * 0.60f)
        lineTo(w * 0.66f, h * 0.44f)
        lineTo(w * 0.88f, h * 0.58f)
        lineTo(w, h * 0.52f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        near,
        Brush.verticalGradient(
            listOf(Palette.MountainNear.copy(alpha = 0.85f), Palette.NightBottom),
            startY = h * 0.44f,
        ),
    )
}
