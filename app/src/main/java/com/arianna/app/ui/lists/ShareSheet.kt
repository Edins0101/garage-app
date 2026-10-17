package com.arianna.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.domain.model.Group
import com.arianna.app.domain.model.GroupShare
import com.arianna.app.domain.model.MemberRole
import com.arianna.app.domain.model.MemberStatus
import com.arianna.app.domain.model.ShareInvite
import com.arianna.app.domain.model.SharedMember
import com.arianna.app.ui.common.appFieldColors
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.Dates
import java.time.Instant
import java.time.ZoneId

private fun roleLabel(role: MemberRole) = if (role == MemberRole.EDITOR) "Puede editar" else "Solo ver"

/** Compartir con mis grupos desde la hoja: cada grupo en "No", "Puede editar" o "Solo ver". */
data class GroupSharing(
    val groups: List<Group>,
    /** Con qué grupos ya está compartido. */
    val shared: List<GroupShare>,
    val onShare: (Group, MemberRole?) -> Unit,
    /** Cómo se llama el permiso de editar (en "¿Está libre?" es "Puede usarlo"). */
    val editorLabel: String = "Puede editar",
    /** Abre la pantalla de grupos (para crear uno). Va al final: se pasa como lambda. */
    val onOpenGroups: () -> Unit,
)

/**
 * Hoja para compartir una lista o una sección de recordatorios: código, correo y miembros.
 * [onSendText] null oculta "Enviar como mensaje"; [extra] agrega contenido al final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    title: String,
    members: List<SharedMember>,
    invite: ShareInvite?,
    busy: Boolean,
    demoMode: Boolean,
    onSendText: (() -> Unit)?,
    onCreateInvite: () -> Unit,
    onShareInvite: (ShareInvite) -> Unit,
    onInvite: (String, MemberRole, () -> Unit) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDismiss: () -> Unit,
    intro: String? = null,
    codeHint: String = "La otra persona lo escribe en Listas → Unirme para ver esta lista.",
    emptyMembers: String = "Todavía nadie más ve esta lista.",
    /** Contenido debajo del título (ej. elegir qué categoría compartir). */
    header: @Composable ColumnScope.() -> Unit = {},
    /** Compartir con un grupo (Familia, Roomies…); null lo oculta. */
    groups: GroupSharing? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(MemberRole.EDITOR) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
            if (intro != null) Text(intro, color = Palette.InkSoft, fontSize = 14.sp)
            header()

            if (demoMode) {
                Text(
                    "Modo demo: sin backend, las invitaciones solo se guardan en este teléfono." +
                        if (onSendText != null) " Enviar la lista como mensaje sí funciona." else "",
                    color = Palette.Ink,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Lavender)
                        .padding(12.dp),
                )
            }

            if (groups != null) GroupSection(groups)

            if (onSendText != null) {
                Section("Enviar como mensaje") {
                    Text("Manda la lista por WhatsApp, SMS o correo.", color = Palette.InkSoft, fontSize = 14.sp)
                    PillButton("Enviar lista", onClick = onSendText, icon = Icons.AutoMirrored.Outlined.Send, modifier = Modifier.fillMaxWidth())
                }
            }

            Section("Código de invitación") {
                if (invite != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.LavenderSoft)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(invite.code, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, color = Palette.Violet)
                        val expires = Instant.parse(invite.expiresAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text("Vence el ${Dates.longDay(expires)}", color = Palette.InkSoft, fontSize = 13.sp)
                    }
                    PillButton("Compartir código", onClick = { onShareInvite(invite) }, light = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        codeHint,
                        color = Palette.InkSoft,
                        fontSize = 14.sp,
                    )
                    PillButton(
                        if (busy) "Generando…" else "Generar código",
                        onClick = onCreateInvite,
                        enabled = !busy,
                        icon = Icons.Outlined.VpnKey,
                        light = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Section("Invitar por correo") {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("correo@ejemplo.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = appFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberRole.entries.forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { role = option },
                            label = { Text(roleLabel(option)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Palette.Violet,
                                selectedLabelColor = Palette.OnDark,
                                labelColor = Palette.Ink,
                            ),
                        )
                    }
                }
                PillButton(
                    "Invitar",
                    onClick = { onInvite(email, role) { email = "" } },
                    enabled = email.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Compartida con (${members.size})") {
                if (members.isEmpty()) {
                    Text(emptyMembers, color = Palette.InkSoft, fontSize = 14.sp)
                }
                members.forEach { member ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name ?: member.email, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${roleLabel(member.role)} · " +
                                    if (member.status == MemberStatus.ACCEPTED) "Aceptó" else "Invitación pendiente",
                                color = Palette.InkSoft,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            "QUITAR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Danger,
                            modifier = Modifier
                                .clickable { onRemoveMember(member.email) }
                                .padding(8.dp),
                        )
                    }
                    HorizontalDivider(color = Palette.Divider)
                }
            }
            extra()
        }
    }
}

/** También la usa el detalle de "¿Está libre?" (los equipos solo se comparten con grupos). */
@Composable
internal fun GroupSection(sharing: GroupSharing) {
    Section("Con un grupo") {
        if (sharing.groups.isEmpty()) {
            Text(
                "Crea un grupo (Familia, Roomies…) y comparte con todos de una vez. Quien entre después también lo verá.",
                color = Palette.InkSoft,
                fontSize = 14.sp,
            )
            PillButton("Crear un grupo", onClick = sharing.onOpenGroups, icon = Icons.Outlined.Groups, light = true, modifier = Modifier.fillMaxWidth())
            return@Section
        }
        sharing.groups.forEach { group ->
            val current = sharing.shared.find { it.groupId == group.id }?.role
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(group.name, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    if (group.size == 1) "Solo tú por ahora" else "${group.size} personas",
                    color = Palette.InkSoft,
                    fontSize = 13.sp,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<MemberRole?>(null, MemberRole.EDITOR, MemberRole.VIEWER).forEach { option ->
                        FilterChip(
                            selected = current == option,
                            onClick = { if (current != option) sharing.onShare(group, option) },
                            label = {
                                Text(
                                    when (option) {
                                        null -> "No"
                                        MemberRole.EDITOR -> sharing.editorLabel
                                        else -> roleLabel(option)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Palette.Violet,
                                selectedLabelColor = Palette.OnDark,
                                labelColor = Palette.Ink,
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(color = Palette.Divider)
        }
        Text(
            "ADMINISTRAR GRUPOS",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Violet,
            modifier = Modifier
                .clickable(onClick = sharing.onOpenGroups)
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetTitle(title)
        Spacer(Modifier.height(2.dp))
        content()
    }
}
