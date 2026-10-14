package com.garaje.app.ui.lists

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.core.content.FileProvider
import com.garaje.app.domain.model.Product
import com.garaje.app.util.ImageCompressor
import java.io.File
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
        ListDetailViewModel(
            createSavedStateHandle(),
            it.lists,
            it.sharing,
            demoMode = it.useFakes,
            currentUserId = it.auth.session.value?.user?.id,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    val context = LocalContext.current

    var sharing by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ChecklistItem?>(null) }
    // Menú del elemento (toque largo), visor de foto y enlace de imagen.
    var actionsFor by remember { mutableStateOf<ChecklistItem?>(null) }
    var viewing by remember { mutableStateOf<ChecklistItem?>(null) }
    var urlFor by remember { mutableStateOf<ChecklistItem?>(null) }
    // Elemento y destino de la foto que se está eligiendo (galería o cámara).
    var photoRequest by remember { mutableStateOf<Pair<ChecklistItem, PhotoTarget>?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var newText by rememberSaveable { mutableStateOf("") }
    var newQuantity by rememberSaveable { mutableStateOf("") }

    val upload = { uri: Uri ->
        photoRequest?.let { (item, target) ->
            viewModel.attachPhoto(item, target) { ImageCompressor.compress(context, uri) }
        }
        photoRequest = null
    }
    // Selector de fotos de Android: no pide permisos.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) upload(uri) else photoRequest = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) upload(uri) else photoRequest = null
    }
    val pickFromGallery = { item: ChecklistItem, target: PhotoTarget ->
        photoRequest = item to target
        actionsFor = null
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val takePhoto = { item: ChecklistItem, target: PhotoTarget ->
        photoRequest = item to target
        actionsFor = null
        val uri = newCameraUri(context)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

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

                    if (state.canEdit) {
                        AddItemRow(
                            isShopping = isShopping,
                            text = newText,
                            quantity = newQuantity,
                            onTextChange = {
                                newText = it
                                viewModel.onQueryChange(it)
                            },
                            onQuantityChange = { newQuantity = it },
                            onAdd = {
                                if (newText.isNotBlank()) {
                                    viewModel.addItem(newText, newQuantity.takeIf { isShopping && it.isNotBlank() })
                                    newText = ""
                                    newQuantity = ""
                                    viewModel.onQueryChange("")
                                }
                            },
                        )
                        SuggestionsRow(state.suggestions) { product ->
                            newText = product.name
                            viewModel.onQueryChange("")
                        }
                    }
                    if (state.uploading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Palette.VioletBright, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Subiendo foto…", color = Palette.OnDarkSoft, fontSize = 13.sp)
                        }
                    }
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
                        ItemRow(
                            item,
                            onToggle = { viewModel.toggle(item) },
                            onLongClick = { actionsFor = item },
                            onImageClick = { viewing = item },
                        )
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
                            ItemRow(
                                item,
                                onToggle = { viewModel.toggle(item) },
                                onLongClick = { actionsFor = item },
                                onImageClick = { viewing = item },
                            )
                        }
                    }

                    Text(
                        "Toca para marcar · mantén presionado para fotos o eliminar",
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
    actionsFor?.let { item ->
        ItemActionsSheet(
            item = item,
            canEdit = state.canEdit,
            onGallery = { target -> pickFromGallery(item, target) },
            onCamera = { target -> takePhoto(item, target) },
            onUrl = {
                actionsFor = null
                urlFor = item
            },
            onRemoveProductPhoto = {
                actionsFor = null
                viewModel.removeProductPhoto(item)
            },
            onRemoveItemPhoto = {
                actionsFor = null
                viewModel.removeItemPhoto(item)
            },
            onDelete = {
                actionsFor = null
                deleting = item
            },
            onDismiss = { actionsFor = null },
        )
    }
    urlFor?.let { item ->
        TextInputDialog(
            title = "Imagen de \"${item.text}\"",
            description = "Pega el enlace directo de una imagen (https://…). Si el sitio la quita, la foto dejará de verse.",
            label = "https://…",
            confirmText = "Guardar",
            capitalization = KeyboardCapitalization.None,
            onConfirm = {
                urlFor = null
                viewModel.attachPhotoUrl(item, it)
            },
            onDismiss = { urlFor = null },
        )
    }
    viewing?.let { item ->
        item.image?.let { image -> ImageViewerDialog(image, item.text, onDismiss = { viewing = null }) }
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
private fun AddItemRow(
    isShopping: Boolean,
    text: String,
    quantity: String,
    onTextChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassField(
            value = text,
            onValueChange = onTextChange,
            placeholder = if (isShopping) "¿Qué hay que comprar?" else "¿Qué hay que hacer?",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            onDone = onAdd,
            modifier = Modifier.weight(1f),
        )
        if (isShopping) {
            Spacer(Modifier.width(8.dp))
            GlassField(
                value = quantity,
                onValueChange = onQuantityChange,
                placeholder = "Cant.",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onDone = onAdd,
                modifier = Modifier.width(84.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        SquareIconButton(Icons.Outlined.Add, "Agregar", onClick = onAdd)
    }
}

/** Sugerencias del catálogo mientras se escribe (con miniatura si el producto tiene foto). */
@Composable
private fun SuggestionsRow(products: List<Product>, onPick: (Product) -> Unit) {
    if (products.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(products, key = { it.id }) { product ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.GlassStrong)
                    .clickable { onPick(product) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (product.image != null) {
                    RemoteImage(product.image, Modifier.size(28.dp).clip(CircleShape))
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(product.name, color = Palette.OnDark, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ItemRow(item: ChecklistItem, onToggle: () -> Unit, onLongClick: () -> Unit, onImageClick: () -> Unit) {
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
            item.image?.let { image ->
                Spacer(Modifier.width(10.dp))
                ItemThumbnail(image, onClick = onImageClick)
            }
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

/** Archivo temporal donde la cámara guarda la foto (cache/camera, ver res/xml/file_paths.xml). */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "foto-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartir lista"))
}
