package com.garaje.app.ui.lists

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.garaje.app.domain.model.ChecklistItem
import com.garaje.app.domain.model.ChecklistKind
import com.garaje.app.ui.common.CollectMessages
import com.garaje.app.ui.common.ConfirmDialog
import com.garaje.app.ui.common.TextInputDialog
import com.garaje.app.ui.common.appViewModel
import com.garaje.app.ui.components.CosmicBackground
import com.garaje.app.ui.components.GlassCard
import com.garaje.app.ui.components.GlassField
import com.garaje.app.ui.components.SheetTitle
import com.garaje.app.ui.components.SquareIconButton
import com.garaje.app.ui.components.TopBar
import com.garaje.app.ui.components.ScrollingSheetLayout
import com.garaje.app.ui.components.combinedClickableCompat
import com.garaje.app.ui.theme.Palette

@Composable
fun ListDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        ListDetailViewModel(createSavedStateHandle(), it.lists, it.sharing, demoMode = it.useFakes)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    val context = LocalContext.current

    var sharing by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ChecklistItem?>(null) }

    val list = state.list

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { padding ->
            if (list == null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TopBar(title = "", onBack = onBack)
                    Spacer(Modifier.height(48.dp))
                    if (state.loaded) {
                        Text("No encontramos esta lista.", color = Palette.OnDark)
                    } else {
                        CircularProgressIndicator(color = Palette.VioletBright)
                    }
                }
                return@Scaffold
            }

            val isShopping = list.kind == ChecklistKind.SHOPPING
            ScrollingSheetLayout(
                header = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TopBar(
                        title = list.title,
                        overline = if (isShopping) "Lista de compras" else "Lista de tareas",
                        onBack = onBack,
                    ) {
                        SquareIconButton(Icons.Outlined.Edit, "Renombrar", onClick = { renaming = true }, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        SquareIconButton(Icons.Outlined.Share, "Compartir", onClick = { sharing = true }, size = 40.dp)
                    }

                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${list.doneCount} DE ${list.items.size} LISTOS",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.OnDark,
                                modifier = Modifier.weight(1f),
                            )
                            if (list.sharedWith.isNotEmpty()) {
                                Text(
                                    "👥 ${list.sharedWith.size}",
                                    color = Palette.OnDarkSoft,
                                    modifier = Modifier.clickable { sharing = true },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { list.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Palette.Success,
                            trackColor = Palette.GlassStrong,
                            drawStopIndicator = {},
                        )
                    }

                    AddItemRow(
                        isShopping = isShopping,
                        onAdd = { text, quantity -> viewModel.addItem(text, quantity) },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                },
                sheet = {
                    SheetTitle(if (isShopping) "Por comprar (${state.pending.size})" else "Por hacer (${state.pending.size})")
                    if (list.items.isEmpty()) {
                        Text(
                            "La lista está vacía. Agrega el primer elemento arriba.",
                            color = Palette.InkSoft,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else if (state.pending.isEmpty()) {
                        Text(
                            "🎉 ¡Todo listo!",
                            color = Palette.Success,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 16.dp),
                        )
                    }
                    state.pending.forEach { item ->
                        ItemRow(item, onToggle = { viewModel.toggle(item) }, onLongClick = { deleting = item })
                    }

                    if (state.done.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        SheetTitle(if (isShopping) "En el carrito (${state.done.size})" else "Hecho (${state.done.size})") {
                            Text(
                                "LIMPIAR",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Violet,
                                modifier = Modifier
                                    .clickable(onClick = viewModel::clearCompleted)
                                    .padding(4.dp),
                            )
                        }
                        state.done.forEach { item ->
                            ItemRow(item, onToggle = { viewModel.toggle(item) }, onLongClick = { deleting = item })
                        }
                    }

                    Text(
                        "Toca para marcar · mantén presionado para eliminar",
                        color = Palette.InkSoft,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 20.dp),
                    )
                }
            )
        }
    }

    if (sharing && list != null) {
        ShareSheet(
            list = list,
            invite = state.invite,
            busy = state.busy,
            demoMode = viewModel.demoMode,
            onSendText = { shareText(context, viewModel.shareText()) },
            onCreateInvite = viewModel::createInvite,
            onShareInvite = { invite ->
                shareText(
                    context,
                    "Te comparto mi lista \"${list.title}\" en Garaje. Únete con el código ${invite.code}: ${invite.url}",
                )
            },
            onInvite = viewModel::invite,
            onRemoveMember = viewModel::removeMember,
            onDismiss = { sharing = false },
        )
    }
    if (renaming && list != null) {
        TextInputDialog(
            title = "Renombrar lista",
            label = "Nombre",
            confirmText = "Guardar",
            initial = list.title,
            onConfirm = {
                renaming = false
                viewModel.rename(it)
            },
            onDismiss = { renaming = false },
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Eliminar",
            message = "¿Quitar \"${item.text}\" de la lista?",
            confirmText = "Eliminar",
            onConfirm = { viewModel.remove(item) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun AddItemRow(isShopping: Boolean, onAdd: (String, String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text, quantity.takeIf { isShopping && it.isNotBlank() })
            text = ""
            quantity = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassField(
            value = text,
            onValueChange = { text = it },
            placeholder = if (isShopping) "¿Qué hay que comprar?" else "¿Qué hay que hacer?",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            onDone = submit,
            modifier = Modifier.weight(1f),
        )
        if (isShopping) {
            Spacer(Modifier.width(8.dp))
            GlassField(
                value = quantity,
                onValueChange = { quantity = it },
                placeholder = "Cant.",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onDone = submit,
                modifier = Modifier.width(84.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        SquareIconButton(Icons.Outlined.Add, "Agregar", onClick = submit)
    }
}

@Composable
private fun ItemRow(item: ChecklistItem, onToggle: () -> Unit, onLongClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickableCompat(onClick = onToggle, onLongClick = onLongClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (item.done) Palette.Violet else Color.Transparent)
                    .border(2.dp, if (item.done) Palette.Violet else Palette.InkSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) {
                    Icon(Icons.Outlined.Check, null, tint = Palette.OnDark, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.text,
                    color = if (item.done) Palette.InkSoft else Palette.Ink,
                    fontSize = 16.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                item.quantity?.let { Text(it, color = Palette.InkSoft, fontSize = 13.sp) }
            }
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartir lista"))
}
