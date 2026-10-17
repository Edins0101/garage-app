package com.arianna.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.arianna.app.ui.counters.CountersScreen
import com.arianna.app.ui.groups.GroupScreen
import com.arianna.app.ui.groups.GroupsScreen
import com.arianna.app.domain.model.GroupItemType
import com.arianna.app.ui.garage.GarageScreen
import com.arianna.app.ui.home.HomeScreen
import com.arianna.app.ui.lists.JoinScreen
import com.arianna.app.ui.lists.ListDetailScreen
import com.arianna.app.ui.lists.ListsScreen
import com.arianna.app.domain.model.ReminderKind
import com.arianna.app.ui.reminders.RemindersScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

/** Lista de contadores por día. */
@Serializable
object CountersRoute

/** Calendario y cuenta de un contador (el garaje es uno más). */
@Serializable
data class CounterRoute(val id: String)

/** filter: "shopping" | "todo" | "shared" | null (todas). */
@Serializable
data class ListsRoute(
    val filter: String? = null,
    val query: String? = null,
    val create: Boolean = false,
    val join: Boolean = false,
)

@Serializable
data class ListDetailRoute(val id: String)

/** kind: "all" | "payments" | "home" | "car" | "other". También se abre con arianna://reminders/payments (avisos). */
@Serializable
data class RemindersRoute(val kind: String)

/** También se abre con el deep link arianna://join/CODIGO */
@Serializable
data class JoinRoute(val code: String)

/** Mis grupos (Familia, Roomies…). */
@Serializable
object GroupsRoute

@Serializable
data class GroupRoute(val id: String)

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenCounters = { navController.navigate(CountersRoute) },
                onOpenCounter = { id -> navController.navigate(CounterRoute(id)) },
                onOpenReminders = { kind -> navController.navigate(RemindersRoute(kind?.apiName ?: "all")) },
                onOpenLists = { route -> navController.navigate(route) },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
                onOpenGroups = { navController.navigate(GroupsRoute) },
                onJoinCode = { code -> navController.navigate(JoinRoute(code)) },
            )
        }
        composable<GroupsRoute> {
            GroupsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(GroupRoute(id)) },
            )
        }
        composable<GroupRoute> { entry ->
            GroupScreen(
                groupId = entry.toRoute<GroupRoute>().id,
                onBack = { if (!navController.popBackStack()) navController.navigate(HomeRoute) },
                onOpenItem = { item ->
                    navController.navigate(
                        when (item.type) {
                            GroupItemType.LIST -> ListDetailRoute(item.id)
                            GroupItemType.SPACE -> RemindersRoute(item.kind)
                            GroupItemType.TRACKER -> CounterRoute(item.id)
                        },
                    )
                },
            )
        }
        composable<CountersRoute> {
            CountersScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(CounterRoute(id)) },
            )
        }
        composable<CounterRoute>(
            // Al tocar el aviso de un pago en un contador compartido.
            deepLinks = listOf(navDeepLink<CounterRoute>(basePath = "arianna://counter")),
        ) { entry ->
            GarageScreen(
                counterId = entry.toRoute<CounterRoute>().id,
                onBack = { navController.popBackStack() },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
                onOpenGroups = { navController.navigate(GroupsRoute) },
            )
        }
        composable<RemindersRoute>(
            deepLinks = listOf(navDeepLink<RemindersRoute>(basePath = "arianna://reminders")),
        ) { entry ->
            RemindersScreen(
                initialFilter = ReminderKind.fromApi(entry.toRoute<RemindersRoute>().kind),
                onBack = { if (!navController.popBackStack()) navController.navigate(HomeRoute) },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
                onOpenGroups = { navController.navigate(GroupsRoute) },
            )
        }
        composable<ListsRoute> { entry ->
            ListsScreen(
                route = entry.toRoute(),
                onBack = { navController.popBackStack() },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
                onJoinCode = { code -> navController.navigate(JoinRoute(code)) },
            )
        }
        composable<ListDetailRoute>(
            // Al tocar una notificación de lista compartida.
            deepLinks = listOf(navDeepLink<ListDetailRoute>(basePath = "arianna://list")),
        ) {
            ListDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenGroups = { navController.navigate(GroupsRoute) },
            )
        }
        composable<JoinRoute>(
            deepLinks = listOf(navDeepLink<JoinRoute>(basePath = "arianna://join")),
        ) { entry ->
            JoinScreen(
                code = entry.toRoute<JoinRoute>().code,
                onJoined = { result ->
                    val target: Any = result.listId?.let(::ListDetailRoute)
                        ?: result.trackerId?.let(::CounterRoute)
                        ?: result.groupId?.let(::GroupRoute)
                        ?: RemindersRoute(result.kind?.apiName ?: "all")
                    navController.navigate(target) {
                        popUpTo<JoinRoute> { inclusive = true }
                    }
                },
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(HomeRoute)
                },
            )
        }
    }
}
