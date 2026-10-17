package com.arianna.app.ui.lists

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.components.ItemRowSkeleton
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.SkeletonBox
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.common.shareText
import com.arianna.app.ui.common.Load
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.core.content.FileProvider
import com.arianna.app.domain.model.Product
import com.arianna.app.util.ImageCompressor
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
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.TextInputDialog
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.GlassField
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.SquareIconButton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.combinedClickableCompat
import com.arianna.app.ui.theme.Palette

@Composable
fun ListDetailScreen(onBack: () -> Unit, onOpenGroups: () -> Unit) {
    val viewModel = appViewModel {
        ListDetailViewModel(
            createSavedStateHandle(),
            it.lists,
            it.sharing,
            it.groups,
            it.undoableDeletes,
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
    var editing by remember { mutableStateOf<ChecklistItem?>(null) }
    // Menú del elemento (toque largo), visor de foto y enlace de imagen.
    var actionsFor by remember { mutableStateOf<ChecklistItem?>(null) }
    /** Sección en la que se agregan los nuevos elementos (se mantiene para agregar varios seguidos). */
    var addSection by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingSection by rememberSaveable { mutableStateOf(false) }
    var movingSection by remember { mutableStateOf<ChecklistItem?>(null) }
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
                    if (state.loaded) {
                        Spacer(Modifier.height(48.dp))
                        Text("No encontramos esta lista.", color = Palette.OnDark)
                    } else {
                        // Forma de la pantalla mientras carga: título, progreso, campo y elementos.
                        Spacer(Modifier.height(4.dp))
                        SkeletonBox(width = 120.dp, height = 14.dp, onDark = true)
                        Spacer(Modifier.height(10.dp))
                        SkeletonBox(width = 200.dp, height = 26.dp, onDark = true)
                        Spacer(Modifier.height(24.dp))
                        SkeletonBox(height = 72.dp, shape = RoundedCornerShape(20.dp), onDark = true)
                        Spacer(Modifier.height(14.dp))
                        SkeletonBox(height = 46.dp, shape = RoundedCornerShape(22.dp), onDark = true)
                        Spacer(Modifier.height(24.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Palette.Sheet)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            LoadNotice(state.load, onRetry = viewModel::retry)
                            repeat(4) { i -> ItemRowSkeleton(textWidth = if (i % 2 == 0) 170.dp else 120.dp) }
                        }
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
                                    viewModel.addItem(newText, newQuantity.takeIf { isShopping && it.isNotBlank() }, addSection) {
                                        newText = ""
                                        newQuantity = ""
                                        viewModel.onQueryChange("")
                                    }
                                }
                            },
                        )
                        SuggestionsRow(state.suggestions) { product ->
                            newText = product.name
                            viewModel.onQueryChange("")
                        }
                        SectionChips(
                            sections = (list.sections + listOfNotNull(addSection)).distinct(),
                            selected = addSection,
                            onSelect = { addSection = it },
                            onNew = { creatingSection = true },
                        )
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
                    // Agrupado por sección: primero lo que no tiene sección y luego cada sección.
                    val groups = state.pending.groupBy { it.section }
                    val order = listOf<String?>(null) + groups.keys.filterNotNull().sortedBy { it.lowercase() }
                    order.forEach { section ->
                        val items = groups[section] ?: return@forEach
                        if (section != null) SectionHeader(section, items.size)
                        items.forEach { item ->
                            ItemRow(
                                item,
                                onToggle = { viewModel.toggle(item) },
                                onLongClick = { actionsFor = item },
                                onImageClick = { viewing = item },
                            )
                        }
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
                        "Toca para marcar · mantén presionado para editar, sección, fotos o eliminar",
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
            title = "Compartir \"${list.title}\"",
            members = list.sharedWith,
            invite = state.invite,
            busy = state.busy,
            demoMode = viewModel.demoMode,
            onSendText = { shareText(context, viewModel.shareText()) },
            onCreateInvite = viewModel::createInvite,
            onShareInvite = { invite ->
                shareText(
                    context,
                    "Te comparto mi lista \"${list.title}\" en Arianna. Únete con el código ${invite.code}: ${invite.url}",
                )
            },
            onInvite = viewModel::invite,
            onRemoveMember = viewModel::removeMember,
            onDismiss = { sharing = false },
            groups = GroupSharing(state.groups, list.groups, viewModel::shareWithGroup) {
                sharing = false
                onOpenGroups()
            },
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
                viewModel.remove(item)
            },
            onEdit = {
                actionsFor = null
                editing = item
            },
            onDismiss = { actionsFor = null },
            onMoveSection = {
                actionsFor = null
                movingSection = item
            },
        )
    }
    if (creatingSection) {
        TextInputDialog(
            title = "Nueva sección",
            description = "Ej. Baño, Patio, Perros, Limpieza. Lo que agregues después irá ahí.",
            label = "Nombre de la sección",
            confirmText = "Crear",
            onConfirm = {
                creatingSection = false
                addSection = it.trim().takeIf { name -> name.isNotEmpty() }
            },
            onDismiss = { creatingSection = false },
        )
    }
    movingSection?.let { item ->
        MoveSectionDialog(
            item = item,
            sections = list?.sections.orEmpty(),
            onMove = { section ->
                movingSection = null
                viewModel.moveToSection(item, section)
            },
            onDismiss = { movingSection = null },
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
    editing?.let { item ->
        EditItemDialog(
            item = item,
            withQuantity = list?.kind == ChecklistKind.SHOPPING || item.quantity != null,
            onSave = { text, quantity ->
                editing = null
                viewModel.editItem(item, text, quantity)
            },
            onDismiss = { editing = null },
        )
    }
}

/** Cambiar el texto y la cantidad de un elemento. */
@Composable
private fun EditItemDialog(
    item: ChecklistItem,
    withQuantity: Boolean,
    onSave: (text: String, quantity: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(item.text) }
    var quantity by rememberSaveable { mutableStateOf(item.quantity.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        title = { Text("Editar elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withQuantity) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.take(60) },
                        label = { Text("Cantidad (opcional)") },
                        placeholder = { Text("Ej. 2, 1 kg") },
                        singleLine = true,
                        colors = appFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, quantity) }) {
                Text("Guardar", color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
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

/** Elegir en qué sección se agregan los nuevos elementos (sobre el fondo oscuro). */
@Composable
private fun SectionChips(sections: List<String>, selected: String?, onSelect: (String?) -> Unit, onNew: () -> Unit) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Palette.Glass,
        labelColor = Palette.OnDarkSoft,
        selectedContainerColor = Palette.OnDark,
        selectedLabelColor = Palette.Violet,
        iconColor = Palette.OnDarkSoft,
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sección:", color = Palette.OnDarkSoft, fontSize = 13.sp)
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Ninguna", style = MaterialTheme.typography.bodyMedium) },
            colors = colors,
            border = null,
        )
        sections.forEach { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelect(section) },
                label = { Text(section, style = MaterialTheme.typography.bodyMedium) },
                colors = colors,
                border = null,
            )
        }
        FilterChip(
            selected = false,
            onClick = onNew,
            label = { Text("Nueva", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp)) },
            colors = colors,
            border = null,
        )
    }
}

/** Encabezado de una sección dentro de "Por comprar". */
@Composable
private fun SectionHeader(name: String, count: Int) {
    Row(Modifier.padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Category, null, tint = Palette.Violet, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, color = Palette.Violet)
        Spacer(Modifier.width(6.dp))
        Text("$count", color = Palette.InkSoft, fontSize = 13.sp)
    }
}

/** Mover un elemento a otra sección (o a una nueva). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveSectionDialog(item: ChecklistItem, sections: List<String>, onMove: (String?) -> Unit, onDismiss: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        titleContentColor = Palette.Ink,
        title = { Text("Sección de «${item.text}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.Violet,
                    selectedLabelColor = Palette.OnDark,
                    labelColor = Palette.Ink,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = item.section == null,
                        onClick = { onMove(null) },
                        label = { Text("Sin sección", style = MaterialTheme.typography.bodyMedium) },
                        colors = colors,
                    )
                    sections.forEach { section ->
                        FilterChip(
                            selected = item.section == section,
                            onClick = { onMove(section) },
                            label = { Text(section, style = MaterialTheme.typography.bodyMedium) },
                            colors = colors,
                        )
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text("O una nueva (ej. Baño)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = { onMove(newName.trim()) }) {
                Text("Mover", color = Palette.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.InkSoft) }
        },
    )
}
