package com.arianna.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arianna.app.domain.model.ChecklistItem
import com.arianna.app.domain.model.ImageRef
import com.arianna.app.domain.model.ImageSource
import com.arianna.app.ui.theme.Palette

/** Foto con caché por `key`: el enlace firmado cambia en cada respuesta, la clave no. */
@Composable
fun RemoteImage(
    image: ImageRef,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /** Fondo mientras carga (en el visor va transparente sobre negro). */
    placeholder: Color = Palette.LavenderSoft,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(image.url)
            .memoryCacheKey(image.key)
            .diskCacheKey(image.key)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(placeholder),
    )
}

@Composable
fun ItemThumbnail(image: ImageRef, onClick: () -> Unit, size: Dp = 44.dp) {
    RemoteImage(
        image,
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    )
}

/** Visor de la foto a pantalla completa. */
@Composable
fun ImageViewerDialog(image: ImageRef, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            RemoteImage(image, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholder = Color.Transparent)
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar", tint = Color.White) }
            }
            Text(
                if (image.source == ImageSource.ITEM) "Foto solo de este elemento" else "Foto del producto",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

/** Qué foto se quiere poner. */
enum class PhotoTarget { PRODUCT, ITEM }

/** Opciones de un elemento: fotos y eliminar. Se abre con toque largo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemActionsSheet(
    item: ChecklistItem,
    canEdit: Boolean,
    onGallery: (PhotoTarget) -> Unit,
    onCamera: (PhotoTarget) -> Unit,
    onUrl: () -> Unit,
    onRemoveProductPhoto: () -> Unit,
    onRemoveItemPhoto: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onMoveSection: () -> Unit = {},
) {
    val hasOwnPhoto = item.image?.source == ImageSource.ITEM
    val hasProductPhoto = item.image?.source == ImageSource.PRODUCT
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.image?.let {
                    RemoteImage(it, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text(item.text, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                    item.quantity?.let { Text(it, color = Palette.InkSoft) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (canEdit && item.productId != null) {
                SectionLabel("Foto de \"${item.text}\" en todas tus listas")
                ActionRow(Icons.Outlined.PhotoLibrary, "Elegir de la galería") { onGallery(PhotoTarget.PRODUCT) }
                ActionRow(Icons.Outlined.PhotoCamera, "Tomar foto") { onCamera(PhotoTarget.PRODUCT) }
                ActionRow(Icons.Outlined.Link, "Pegar enlace de una imagen", onClick = onUrl)
                if (hasProductPhoto) ActionRow(Icons.Outlined.HideImage, "Quitar foto del producto", onClick = onRemoveProductPhoto)
                HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (canEdit) {
                SectionLabel("Sección")
                ActionRow(Icons.Outlined.Category, item.section?.let { "En $it · cambiar" } ?: "Poner en una sección", onClick = onMoveSection)
                HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Solo en este elemento (ej: otra marca)")
                ActionRow(Icons.Outlined.AddAPhoto, "Foto propia desde la galería") { onGallery(PhotoTarget.ITEM) }
                ActionRow(Icons.Outlined.PhotoCamera, "Foto propia con la cámara") { onCamera(PhotoTarget.ITEM) }
                if (hasOwnPhoto) ActionRow(Icons.Outlined.HideImage, "Quitar foto propia", onClick = onRemoveItemPhoto)
                HorizontalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 8.dp))
                ActionRow(Icons.Outlined.DeleteOutline, "Eliminar de la lista", danger = true, onClick = onDelete)
            } else {
                Text("Solo puedes ver esta lista.", color = Palette.InkSoft)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.InkSoft,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) Palette.Danger else Palette.Violet)
        Spacer(Modifier.width(14.dp))
        Text(text, color = if (danger) Palette.Danger else Palette.Ink, fontSize = 16.sp)
    }
}
