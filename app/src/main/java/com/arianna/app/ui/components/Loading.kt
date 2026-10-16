package com.arianna.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** Brillo que recorre el bloque de izquierda a derecha (efecto "skeleton"). */
fun Modifier.shimmer(base: Color = Palette.DayBox, highlight: Color = Palette.LavenderSoft): Modifier = composed {
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(progress * 600f, 0f),
            end = Offset(progress * 600f + 600f, 0f),
        ),
    )
}

/** Bloque gris animado que ocupa el lugar de un contenido que aún no llega. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(6.dp),
    onDark: Boolean = false,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .then(
                if (onDark) Modifier.shimmer(base = Palette.GlassStrong, highlight = Palette.Glass)
                else Modifier.shimmer(),
            ),
    )
}

/** Imita una tarjeta "boleto" (TicketCard) mientras carga. */
@Composable
fun TicketSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        SkeletonBox(width = 56.dp, height = 22.dp, shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.LavenderSoft),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(width = 160.dp, height = 18.dp)
                SkeletonBox(width = 220.dp, height = 12.dp)
                SkeletonBox(height = 6.dp)
            }
            SkeletonBox(height = 36.dp, shape = RoundedCornerShape(0.dp))
        }
    }
}

/** Imita una fila de elemento de lista (check + texto). */
@Composable
fun ItemRowSkeleton(textWidth: Dp = 180.dp) {
    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(width = 26.dp, height = 26.dp, shape = CircleShape)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(width = textWidth, height = 16.dp)
            SkeletonBox(width = textWidth / 2, height = 12.dp)
        }
    }
}

/**
 * Aviso bajo los esqueletos: si la carga tarda (el servidor gratis "despierta" en ~1 min)
 * explica la espera; si falló, muestra el error con "Reintentar".
 */
@Composable
fun LoadNotice(load: Load, onRetry: () -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(load) {
        slow = false
        if (load == Load.LOADING) {
            delay(4_000)
            slow = true
        }
    }
    val textColor = if (onDark) Palette.OnDarkSoft else Palette.InkSoft

    AnimatedVisibility(visible = load == Load.ERROR || (load == Load.LOADING && slow), enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (load == Load.ERROR) {
                Icon(Icons.Outlined.CloudOff, null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("No se pudo cargar.", color = textColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                PillButton("Reintentar", onClick = onRetry, light = true)
            } else {
                CircularProgressIndicator(
                    color = Palette.Violet,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Despertando el servidor… la primera carga puede tardar hasta un minuto.",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
