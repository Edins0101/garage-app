package com.garaje.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garaje.app.ui.theme.Palette

/**
 * Casillas para el código de verificación. Un solo campo invisible recibe el texto
 * (así funcionan pegar y el autocompletado del teclado) y se dibuja como N casillas.
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    onComplete: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(length)
            onValueChange(digits)
            if (digits.length == length && value.length < length) onComplete()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { index ->
                    val char = value.getOrNull(index)
                    val active = index == value.length
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.82f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (char != null) Palette.Sheet else Palette.GlassStrong)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) Palette.VioletBright else Palette.GlassBorder,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            char?.toString().orEmpty(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Violet,
                        )
                    }
                }
            }
        },
    )
}
