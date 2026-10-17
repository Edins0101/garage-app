package com.arianna.app.ui.appliances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.Load
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.lists.GroupSharing
import com.arianna.app.ui.theme.Palette

/** "¿Está libre?": la lavadora, la secadora, el baño… y quién los está usando. */
@Composable
fun AppliancesScreen(onBack: () -> Unit, onOpenGroups: () -> Unit) {
    val viewModel = appViewModel { AppliancesViewModel(it.appliances, it.groups, it.auth.session.value?.user?.id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    AutoReload(viewModel::reload)
    val now = rememberNow()
    val myId = viewModel.myId

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
                        TopBar(title = "¿Está libre?", overline = "En la casa", onBack = onBack)
                        GlassCard {
                            Text("AHORA", style = MaterialTheme.typography.labelLarge, color = Palette.OnDarkSoft)
                            Text(
                                when {
                                    state.appliances.isEmpty() -> "Nada todavía"
                                    state.inUse == 0 -> "Todo libre"
                                    else -> "${state.free} libre${if (state.free == 1) "" else "s"} · ${state.inUse} en uso"
                                },
                                color = Palette.OnDark,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 36.sp,
                            )
                            Text(
                                "Avisa a tu grupo cuando ocupas o liberas algo.",
                                color = Palette.OnDarkSoft,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            PillButton(
                                "Nuevo equipo",
                                onClick = { viewModel.open(ApplianceDialog.Create) },
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
                    SheetTitle("Lavadora, secadora, baño…")
                    Spacer(Modifier.height(12.dp))
                    when {
                        state.load != Load.READY && state.appliances.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TicketSkeleton()
                            TicketSkeleton()
                        }
                        state.appliances.isEmpty() -> Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.LocalLaundryService, null, tint = Palette.Violet)
                            Text(
                                "Soy Arianna. Agrega la lavadora, la secadora o el baño, compártelos con tu grupo " +
                                    "y todos sabrán si están libres. Les aviso cuando alguien los ocupe o los libere.",
                                color = Palette.InkSoft,
                            )
                            PillButton("Nuevo equipo", onClick = { viewModel.open(ApplianceDialog.Create) }, icon = Icons.Outlined.AddCircleOutline)
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.appliances.forEach { appliance ->
                                ApplianceCard(
                                    appliance = appliance,
                                    myId = myId,
                                    now = now,
                                    busy = state.busy,
                                    onAction = { action ->
                                        when (action) {
                                            ApplianceAction.START -> viewModel.open(ApplianceDialog.Start(appliance.id))
                                            ApplianceAction.FINISH -> viewModel.finish(appliance)
                                            ApplianceAction.RELEASE -> viewModel.open(ApplianceDialog.Release(appliance.id))
                                            ApplianceAction.NONE -> Unit
                                        }
                                    },
                                    onClick = { viewModel.open(ApplianceDialog.Detail(appliance.id)) },
                                )
                            }
                            Text(
                                "Toca un equipo para tus avisos, el historial o compartirlo.",
                                color = Palette.InkSoft,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }

    val appliance = state.dialogAppliance
    when (val dialog = state.dialog) {
        ApplianceDialog.Create -> ApplianceDialog(
            appliance = null,
            busy = state.busy,
            onSave = viewModel::create,
            onDismiss = { viewModel.open(null) },
        )
        is ApplianceDialog.Edit -> if (appliance != null) ApplianceDialog(
            appliance = appliance,
            busy = state.busy,
            onSave = { viewModel.update(appliance, it) },
            onDismiss = { viewModel.open(ApplianceDialog.Detail(appliance.id)) },
        )
        is ApplianceDialog.Start -> if (appliance != null) StartDialog(
            appliance = appliance,
            onStart = { minutes -> viewModel.start(appliance, minutes) },
            onDismiss = { viewModel.open(null) },
        )
        is ApplianceDialog.Release -> if (appliance != null) ReleaseDialog(
            appliance = appliance,
            onConfirm = { viewModel.finish(appliance) },
            onDismiss = { viewModel.open(null) },
        )
        is ApplianceDialog.Detail -> if (appliance != null) ApplianceDetailSheet(
            appliance = appliance,
            myId = myId,
            history = state.history,
            sharing = GroupSharing(
                groups = state.groups,
                shared = appliance.groups,
                onShare = { group, role -> viewModel.shareWithGroup(appliance, group, role) },
                onOpenGroups = {
                    viewModel.open(null)
                    onOpenGroups()
                },
                editorLabel = "Puede usarlo",
            ).takeIf { appliance.mine },
            onNotify = { mode -> viewModel.setNotify(appliance, mode) },
            onEdit = { viewModel.open(ApplianceDialog.Edit(appliance.id)) },
            onDelete = { viewModel.open(ApplianceDialog.Delete(appliance.id)) },
            onDismiss = { viewModel.open(null) },
        )
        is ApplianceDialog.Delete -> if (appliance != null) ConfirmDialog(
            title = "Eliminar ${appliance.name}",
            message = "Se borra también su historial y deja de verse en tus grupos.",
            confirmText = "Eliminar",
            onConfirm = { viewModel.delete(appliance) },
            onDismiss = { viewModel.open(ApplianceDialog.Detail(appliance.id)) },
        )
        null -> Unit
    }
}
