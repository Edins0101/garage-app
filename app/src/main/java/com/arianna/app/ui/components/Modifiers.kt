package com.arianna.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/** Toque y toque largo; si no hay acción larga se comporta como un clic normal. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)
