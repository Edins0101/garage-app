package com.garaje.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garaje.app.ui.theme.Palette

/** Barra superior: atrás, etiqueta pequeña, título grande y acciones. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Palette.OnDark)
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (overline != null) {
                Text(
                    overline.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 2.sp),
                    color = Palette.OnDarkSoft,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.OnDark,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Título de sección en mayúsculas con línea inferior, como "MAIN MENU". */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Palette.OnDark, underline: Boolean = true) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = color)
        if (underline) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(color.copy(alpha = 0.7f)),
            )
        }
    }
}

/** Botón cuadrado blanco con ícono violeta (el "filtro" del diseño). */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Sheet)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Palette.Violet)
    }
}

/** Campo de texto sobre fondo oscuro ("píldora" translúcida). */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = Palette.OnDark, fontSize = 16.sp),
        cursorBrush = SolidColor(Palette.VioletBright),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onSearch = { onDone?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Palette.GlassStrong)
                    .border(1.dp, Palette.GlassBorder, RoundedCornerShape(22.dp))
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, tint = Palette.OnDarkSoft, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Palette.OnDarkSoft.copy(alpha = 0.7f), fontSize = 15.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

/** Panel translúcido con borde suave para agrupar controles sobre el fondo. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(20.dp))
            .padding(contentPadding),
        content = content,
    )
}

/** Mosaico violeta del menú principal, con ícono de línea y etiqueta. */
@Composable
fun MenuTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 22.dp)
    Column(
        modifier = modifier
            .aspectRatio(if (compact) 1.05f else 0.95f)
            .shadow(if (compact) 0.dp else 10.dp, shape, ambientColor = Palette.VioletDeep, spotColor = Palette.VioletDeep)
            .clip(shape)
            .background(
                if (compact && !selected) {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                } else {
                    Brush.verticalGradient(listOf(Palette.Violet, Palette.VioletDeep))
                },
            )
            .then(if (selected) Modifier.border(BorderStroke(2.dp, Palette.OnDark), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Palette.OnDark,
            modifier = Modifier.size(if (compact) 34.dp else 46.dp),
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.OnDark,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Hoja blanca inferior con "manija", como la sección SCHEDULE del diseño. */
@Composable
fun WhiteSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Palette.Sheet)
            // El blanco llega hasta el borde inferior; el contenido evita la barra de navegación.
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(64.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Palette.DayBox),
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/**
 * Estructura común de las pantallas: encabezado sobre el fondo oscuro y hoja
 * blanca debajo. Todo se desplaza junto, y la hoja se estira hasta el borde
 * inferior aunque tenga poco contenido.
 */
@Composable
fun ScrollingSheetLayout(
    header: @Composable ColumnScope.() -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(0.dp) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                content = header,
            )
            WhiteSheet(
                Modifier.heightIn(min = (screenHeight - headerHeight).coerceAtLeast(0.dp)),
                content = sheet,
            )
        }
        // Franja bajo la barra de estado para que el contenido no choque con la hora.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Palette.NightTop.copy(alpha = 0.92f)),
        )
    }
}

/** Título de sección dentro de la hoja blanca. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Divider),
        )
    }
}

/**
 * Tarjeta tipo "boleto" (como BEST OPTIONS): cuerpo lavanda, etiqueta
 * superior opcional y barra violeta inferior con acción.
 */
@Composable
fun TicketCard(
    footer: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
    footerIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.OnDark,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Palette.Violet)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Lavender)
                .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), content = content)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Violet)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Palette.NightTop),
                )
                Spacer(Modifier.width(8.dp))
                if (footerIcon != null) {
                    Icon(footerIcon, null, tint = Palette.OnDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    footer.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.OnDark,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.OnDark)
            }
        }
    }
}

/** Botón principal violeta en forma de píldora. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    light: Boolean = false,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                when {
                    !enabled -> Palette.DayBox
                    light -> Palette.LavenderSoft
                    else -> Palette.Violet
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (light) Palette.Violet else Palette.OnDark
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Avatar circular violeta con ícono. */
@Composable
fun CircleAvatar(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Palette.VioletBright, Palette.Violet))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.OnDark, modifier = Modifier.size(size * 0.55f))
    }
}
