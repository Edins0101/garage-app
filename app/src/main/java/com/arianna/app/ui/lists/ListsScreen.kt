package com.arianna.app.ui.lists

import com.arianna.app.ui.components.TicketSkeleton
import com.arianna.app.ui.components.SkeletonBox
import com.arianna.app.ui.components.LoadNotice
import com.arianna.app.ui.common.Load
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arianna.app.domain.model.Checklist
import com.arianna.app.domain.model.ChecklistKind
import com.arianna.app.ui.common.CollectMessages
import com.arianna.app.ui.common.ConfirmDialog
import com.arianna.app.ui.common.NewListDialog
import com.arianna.app.ui.common.TextInputDialog
import com.arianna.app.ui.common.appViewModel
import com.arianna.app.ui.components.CosmicBackground
import com.arianna.app.ui.components.GlassCard
import com.arianna.app.ui.components.GlassField
import com.arianna.app.ui.components.MenuTile
import com.arianna.app.ui.components.PillButton
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.components.TopBar
import com.arianna.app.ui.components.ScrollingSheetLayout
import com.arianna.app.ui.home.ChecklistTicket
import com.arianna.app.ui.navigation.ListsRoute
import com.arianna.app.ui.theme.Palette

@Composable
fun ListsScreen(
    route: ListsRoute,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    /** Abre la pantalla de unirse: sirve para códigos de listas y de secciones (Pagos). */
    onJoinCode: (String) -> Unit,
) {
    val viewModel = appViewModel { ListsViewModel(route.filter, route.query, it.lists) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel, snackbar)
    LaunchedEffect(viewModel) { viewModel.openList.collect(onOpenList) }

    var creating by rememberSaveable { mutableStateOf(route.create) }
    var joining by rememberSaveable { mutableStateOf(route.join) }
    var deleting by remember { mutableStateOf<Checklist?>(null) }

    CosmicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { creating = true },
                    containerColor = Palette.Violet,
                    contentColor = Palette.OnDark,
                    modifier = Modifier.navigationBarsPadding(),
                ) { Icon(Icons.Outlined.Add, "Nueva lista") }
            },
        ) { padding ->
            ScrollingSheetLayout(
                header = {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TopBar(title = "Mis listas", overline = "Compras y tareas", onBack = onBack)

                    GlassField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Buscar listas o elementos…",
                        leadingIcon = Icons.Outlined.Search,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )

                    GlassCard {
                        Text("FILTRAR", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = Palette.OnDark)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterTile(Icons.Outlined.Apps, "Todas", ListFilter.ALL, state.filter, viewModel::setFilter)
                            FilterTile(Icons.Outlined.ShoppingCart, "Compras", ListFilter.SHOPPING, state.filter, viewModel::setFilter)
                            FilterTile(Icons.Outlined.TaskAlt, "Tareas", ListFilter.TODO, state.filter, viewModel::setFilter)
                            FilterTile(Icons.Outlined.Groups, "Grupo", ListFilter.SHARED, state.filter, viewModel::setFilter)
                        }
                    }

                    GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VpnKey, null, tint = Palette.OnDark)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "¿Te compartieron una lista?",
                                color = Palette.OnDark,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton("Unirme", onClick = { joining = true }, light = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                },
                sheet = {
                    LoadNotice(state.load, onRetry = viewModel::retry)
                    if (!state.loaded) {
                        SheetTitle("Tus listas")
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            repeat(3) { TicketSkeleton() }
                        }
                        Spacer(Modifier.height(88.dp))
                        return@ScrollingSheetLayout
                    }
                    SheetTitle(
                        when (state.lists.size) {
                            0 -> "Sin resultados"
                            1 -> "1 lista"
                            else -> "${state.lists.size} listas"
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    if (state.loaded && state.lists.isEmpty()) {
                        Text(
                            if (state.query.isBlank() && state.filter == ListFilter.ALL) {
                                "Crea tu primera lista con el botón +."
                            } else {
                                "Ninguna lista coincide con la búsqueda o el filtro."
                            },
                            color = Palette.InkSoft,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.lists.forEach { list ->
                            ChecklistTicket(
                                list = list,
                                onClick = { onOpenList(list.id) },
                                onLongClick = { deleting = list },
                            )
                        }
                    }
                    Text(
                        "Mantén presionada una lista para eliminarla.",
                        color = Palette.InkSoft,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp, bottom = 88.dp),
                    )
                }
            )
        }
    }

    if (creating) {
        NewListDialog(
            initialKind = if (state.filter == ListFilter.TODO) ChecklistKind.TODO else ChecklistKind.SHOPPING,
            onCreate = { title, kind ->
                creating = false
                viewModel.create(title, kind)
            },
            onDismiss = { creating = false },
        )
    }
    if (joining) {
        TextInputDialog(
            title = "Unirme",
            description = "Escribe el código de 6 caracteres que te compartieron: de una lista, recordatorios, un calendario o un grupo.",
            label = "Código",
            confirmText = "Unirme",
            capitalization = KeyboardCapitalization.Characters,
            onConfirm = {
                joining = false
                onJoinCode(it.trim().uppercase())
            },
            onDismiss = { joining = false },
        )
    }
    deleting?.let { list ->
        ConfirmDialog(
            title = "Eliminar lista",
            message = "¿Eliminar \"${list.title}\" y todos sus elementos?",
            confirmText = "Eliminar",
            onConfirm = { viewModel.delete(list) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FilterTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: ListFilter,
    current: ListFilter,
    onSelect: (ListFilter) -> Unit,
) {
    MenuTile(
        icon = icon,
        label = label,
        onClick = { onSelect(value) },
        modifier = Modifier.weight(1f),
        selected = value == current,
        compact = true,
    )
}
