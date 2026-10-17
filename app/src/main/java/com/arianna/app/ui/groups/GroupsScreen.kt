package com.arianna.app.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.domain.model.CounterIcon
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.GroupItem
import com.arianna.app.domain.model.GroupItemType
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.repository.GroupsRepository
import com.arianna.app.ui.common.BaseViewModel
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.common.TextInputDialog
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.common.listIcon
import com.arianna.app.ui.common.shareText
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TicketCard
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.counters.counterIcon
import com.arianna.app.ui.reminders.KindCopy
import com.arianna.app.ui.reminders.kindIcon
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

private fun people(group: Group) = if (group.size == 1) "solo tú" else "${group.size} personas"

/** Diálogo abierto en la pantalla de un grupo. */
enum class GroupDialog { CREATE, RENAME, DELETE, LEAVE }

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val dialog: GroupDialog? = null,
    val invite: ShareInvite? = null,
    val busy: Boolean = false,
    val load: Load = Load.LOADING,
)

private data class GroupsLocal(val dialog: GroupDialog? = null, val invite: ShareInvite? = null, val busy: Boolean = false)

/** Mis grupos y, en la pantalla de uno, sus integrantes y lo compartido. */
class GroupsViewModel(
    private val repository: GroupsRepository,
    private val myEmail: String,
    val demoMode: Boolean,
) : BaseViewModel() {
    private val local = MutableStateFlow(GroupsLocal())

    val state: StateFlow<GroupsUiState> = loadingState(GroupsUiState(), { copy(load = it) }) {
        combine(local, repository.observeGroups()) { ui, groups ->
            GroupsUiState(groups, ui.dialog, ui.invite, ui.busy)
        }
    }

    fun open(dialog: GroupDialog?) {
        local.value = local.value.copy(dialog = dialog)
    }

    private fun withBusy(block: suspend () -> Unit) = launchSafely {
        local.value = local.value.copy(busy = true)
        try {
            block()
        } finally {
            local.value = local.value.copy(busy = false)
        }
    }

    fun create(name: String, onCreated: (String) -> Unit) = withBusy {
        val group = repository.create(name)
        open(null)
        onCreated(group.id)
    }

    fun rename(group: Group, name: String) = withBusy {
        repository.rename(group, name)
        open(null)
    }

    fun delete(group: Group, onDeleted: () -> Unit) = withBusy {
        repository.delete(group)
        notify("Grupo «${group.name}» eliminado")
        open(null)
        onDeleted()
    }

    fun leave(group: Group, onLeft: () -> Unit) = withBusy {
        repository.removeMember(group, myEmail)
        notify("Saliste de «${group.name}»")
        open(null)
        onLeft()
    }

    fun addMember(group: Group, email: String, onDone: () -> Unit) = withBusy {
        val member = repository.addMember(group, email)
        notify(
            if (member.status == MemberStatus.ACCEPTED) "${member.name ?: member.email} ya está en «${group.name}»"
            else "Listo: entrará al grupo cuando abra Arianna con ese correo",
        )
        onDone()
    }

    fun removeMember(group: Group, email: String) = launchSafely { repository.removeMember(group, email) }

    fun createInvite(group: Group) = withBusy {
        local.value = local.value.copy(invite = repository.createInvite(group))
    }
}

@Composable
private fun groupsViewModel(): GroupsViewModel = appViewModel(key = "groups") { container ->
    GroupsViewModel(container.groups, container.auth.session.value?.user?.email.orEmpty(), container.useFakes)
}

/** Lista de mis grupos (Familia, Roomies…). */
@Composable
fun GroupsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val viewModel = groupsViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { _ ->
            ScrollingSheetLayout(
                header = {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TopBar(title = "Grupos", overline = "Familia, roomies…", onBack = onBack)
                        GlassCard {
                            Text(
                                "Agrega a tu gente una vez y comparte listas, recordatorios o calendarios con todo el grupo. " +
                                    "Quien entre después también lo verá.",
                                color = Palette.OnDarkSoft,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            PillButton(
                                "Nuevo grupo",
                                onClick = { viewModel.open(GroupDialog.CREATE) },
                                icon = Icons.Outlined.AddCircleOutline,
                                light = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
                    SheetTitle("Mis grupos")
                    Spacer(Modifier.height(12.dp))
                    when {
                        state.load != Load.READY && state.groups.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TicketSkeleton()
                        }
                        state.groups.isEmpty() -> Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.Groups, null, tint = Palette.Violet)
                            Text(
                                "Crea «Familia» con tu pareja y tus hijos, o «Roomies» con quienes vives. " +
                                    "Si alguien te invitó, entra con su código en Inicio → Unirme.",
                                color = Palette.InkSoft,
                            )
                            PillButton("Nuevo grupo", onClick = { viewModel.open(GroupDialog.CREATE) }, icon = Icons.Outlined.AddCircleOutline)
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.groups.forEach { group ->
                                TicketCard(
                                    footer = listOf(
                                        if (group.size == 1) "Solo tú" else "${group.size} personas",
                                        when (group.shared.size) {
                                            0 -> "nada compartido aún"
                                            1 -> "1 cosa compartida"
                                            else -> "${group.shared.size} cosas compartidas"
                                        },
                                    ).joinToString(" · "),
                                    onClick = { onOpen(group.id) },
                                    footerIcon = Icons.Outlined.Groups,
                                ) {
                                    Text(group.name, fontWeight = FontWeight.Bold, color = Palette.Ink)
                                    Text(
                                        if (group.mine) "Lo creaste tú" else "De ${group.ownerName}",
                                        color = Palette.InkSoft,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    if (state.dialog == GroupDialog.CREATE) {
        TextInputDialog(
            title = "Nuevo grupo",
            label = "Nombre (ej. Familia)",
            confirmText = if (state.busy) "Creando…" else "Crear",
            onConfirm = { viewModel.create(it, onOpen) },
            onDismiss = { viewModel.open(null) },
        )
    }
}

private fun itemIcon(item: GroupItem): ImageVector = when (item.type) {
    GroupItemType.LIST -> listIcon(if (item.kind == "todo") ChecklistKind.TODO else ChecklistKind.SHOPPING)
    GroupItemType.SPACE -> kindIcon(ReminderKind.fromApi(item.kind) ?: ReminderKind.OTHER)
    GroupItemType.TRACKER -> counterIcon(CounterIcon.entries.find { it.name.equals(item.kind, ignoreCase = true) } ?: CounterIcon.OTHER)
}

private fun itemTitle(item: GroupItem): String = when (item.type) {
    GroupItemType.LIST -> item.title
    // Las secciones de recordatorios se nombran por su categoría.
    GroupItemType.SPACE -> "Recordatorios: " + KindCopy.of(ReminderKind.fromApi(item.kind) ?: ReminderKind.OTHER).title
    GroupItemType.TRACKER -> item.title
}

/** Un grupo: integrantes, cómo sumar gente y lo que está compartido con él. */
@Composable
fun GroupScreen(groupId: String, onBack: () -> Unit, onOpenItem: (GroupItem) -> Unit) {
    val viewModel = groupsViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    CollectMessages(viewModel, snackbar)
    val group = state.groups.find { it.id == groupId }
    var email by rememberSaveable { mutableStateOf("") }

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        ) { _ ->
            ScrollingSheetLayout(
                header = {
                    Column(
                        Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TopBar(
                            title = group?.name ?: "Grupo",
                            overline = when {
                                group == null -> "Grupo"
                                group.mine -> "Tu grupo · ${people(group)}"
                                else -> "De ${group.ownerName} · ${people(group)}"
                            },
                            onBack = onBack,
                        ) {
                            if (group?.mine == true) {
                                IconButton(onClick = { viewModel.open(GroupDialog.RENAME) }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Cambiar nombre", tint = Palette.OnDark)
                                }
                            } else if (group != null) {
                                IconButton(onClick = { viewModel.open(GroupDialog.LEAVE) }) {
                                    Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = "Salir del grupo", tint = Palette.OnDark)
                                }
                            }
                        }
                        if (group != null) {
                            GlassCard {
                                Text("EN EL GRUPO", style = MaterialTheme.typography.labelLarge, color = Palette.OnDarkSoft)
                                Text(
                                    if (group.size == 1) "Solo tú" else "${group.size} personas",
                                    color = Palette.OnDark,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Lo que compartas con «${group.name}» lo ven todos, también quienes entren después.",
                                    color = Palette.OnDarkSoft,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
                    if (group == null) {
                        if (state.load == Load.READY) Text("Este grupo ya no existe o saliste de él.", color = Palette.InkSoft)
                        return@ScrollingSheetLayout
                    }
                    if (viewModel.demoMode) {
                        Text(
                            "Modo demo: sin backend, las personas que agregues quedan pendientes en este teléfono.",
                            color = Palette.Ink,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.Lavender)
                                .padding(12.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    if (group.mine) {
                        SheetTitle("Sumar a alguien")
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("correo@ejemplo.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = appFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                "Agregar",
                                onClick = { viewModel.addMember(group, email) { email = "" } },
                                enabled = email.isNotBlank() && !state.busy,
                                icon = Icons.Outlined.PersonAdd,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                "Código",
                                onClick = { viewModel.createInvite(group) },
                                enabled = !state.busy,
                                icon = Icons.Outlined.VpnKey,
                                light = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        state.invite?.takeIf { it.groupId == group.id }?.let { invite ->
                            Spacer(Modifier.height(12.dp))
                            InviteCode(invite) {
                                shareText(
                                    context,
                                    "Entra a mi grupo «${group.name}» en Arianna para ver lo que compartimos. " +
                                        "Únete con el código ${invite.code}: ${invite.url}",
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                    SheetTitle("Compartido con el grupo")
                    Spacer(Modifier.height(10.dp))
                    if (group.shared.isEmpty()) {
                        Text(
                            "Todavía nada. Abre una lista, una categoría de recordatorios o un calendario, toca Compartir " +
                                "y elige «${group.name}».",
                            color = Palette.InkSoft,
                            fontSize = 14.sp,
                        )
                    }
                    group.shared.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenItem(item) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(itemIcon(item), null, tint = Palette.Violet, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(itemTitle(item), color = Palette.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "De ${item.ownerName} · " + if (item.role == MemberRole.EDITOR) "pueden editar" else "solo ver",
                                    color = Palette.InkSoft,
                                    fontSize = 13.sp,
                                )
                            }
                            Text("ABRIR", style = MaterialTheme.typography.labelMedium, color = Palette.Violet)
                        }
                        HorizontalDivider(color = Palette.Divider)
                    }

                    Spacer(Modifier.height(22.dp))
                    SheetTitle("Integrantes (${group.size})")
                    Spacer(Modifier.height(6.dp))
                    MemberRow(group.ownerName, "Creó el grupo", onRemove = null)
                    group.members.forEach { member ->
                        MemberRow(
                            member.name ?: member.email,
                            if (member.status == MemberStatus.ACCEPTED) member.email else "Pendiente · entra cuando abra Arianna con ${member.email}",
                            onRemove = if (group.mine) ({ viewModel.removeMember(group, member.email) }) else null,
                        )
                    }

                    if (group.mine) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "ELIMINAR GRUPO",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Danger,
                            modifier = Modifier
                                .clickable { viewModel.open(GroupDialog.DELETE) }
                                .padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    if (group != null) {
        when (state.dialog) {
            GroupDialog.RENAME -> TextInputDialog(
                title = "Nombre del grupo",
                label = "Nombre",
                confirmText = "Guardar",
                initial = group.name,
                onConfirm = { viewModel.rename(group, it) },
                onDismiss = { viewModel.open(null) },
            )
            GroupDialog.DELETE -> ConfirmDialog(
                title = "Eliminar «${group.name}»",
                message = "Lo compartido sigue siendo de cada quien; solo deja de verse en el grupo.",
                confirmText = "Eliminar",
                onConfirm = { viewModel.delete(group, onBack) },
                onDismiss = { viewModel.open(null) },
            )
            GroupDialog.LEAVE -> ConfirmDialog(
                title = "Salir de «${group.name}»",
                message = "Dejarás de ver lo que está compartido con el grupo. ${group.ownerName} puede volver a agregarte.",
                confirmText = "Salir",
                onConfirm = { viewModel.leave(group, onBack) },
                onDismiss = { viewModel.open(null) },
            )
            else -> Unit
        }
    }
}

@Composable
private fun InviteCode(invite: ShareInvite, onShare: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.LavenderSoft)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(invite.code, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, color = Palette.Violet)
        val expires = Instant.parse(invite.expiresAt).atZone(ZoneId.systemDefault()).toLocalDate()
        Text("Se escribe en Inicio → Unirme · vence el ${Dates.longDay(expires)}", color = Palette.InkSoft, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        PillButton("Compartir código", onClick = onShare, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MemberRow(name: String, detail: String, onRemove: (() -> Unit)?) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Palette.InkSoft, fontSize = 13.sp)
        }
        if (onRemove != null) {
            Text(
                "QUITAR",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Danger,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(8.dp),
            )
        }
    }
    HorizontalDivider(color = Palette.Divider)
}
