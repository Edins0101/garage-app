package com.arianna.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.arianna.app.ui.garage.GarageScreen
import com.arianna.app.ui.home.HomeScreen
import com.arianna.app.ui.lists.JoinScreen
import com.arianna.app.ui.lists.ListDetailScreen
import com.arianna.app.ui.lists.ListsScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object GarageRoute

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

/** También se abre con el deep link arianna://join/CODIGO */
@Serializable
data class JoinRoute(val code: String)

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
                onOpenGarage = { navController.navigate(GarageRoute) },
                onOpenLists = { route -> navController.navigate(route) },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
            )
        }
        composable<GarageRoute> {
            GarageScreen(onBack = { navController.popBackStack() })
        }
        composable<ListsRoute> { entry ->
            ListsScreen(
                route = entry.toRoute(),
                onBack = { navController.popBackStack() },
                onOpenList = { id -> navController.navigate(ListDetailRoute(id)) },
            )
        }
        composable<ListDetailRoute>(
            // Al tocar una notificación de lista compartida.
            deepLinks = listOf(navDeepLink<ListDetailRoute>(basePath = "arianna://list")),
        ) {
            ListDetailScreen(onBack = { navController.popBackStack() })
        }
        composable<JoinRoute>(
            deepLinks = listOf(navDeepLink<JoinRoute>(basePath = "arianna://join")),
        ) { entry ->
            JoinScreen(
                code = entry.toRoute<JoinRoute>().code,
                onJoined = { id ->
                    navController.navigate(ListDetailRoute(id)) {
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
