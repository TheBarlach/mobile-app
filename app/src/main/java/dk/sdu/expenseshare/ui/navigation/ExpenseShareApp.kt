package dk.sdu.expenseshare.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.feature.explore.ExploreScreen
import dk.sdu.expenseshare.feature.home.HomeScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/** Type-safe route definitions for Navigation Compose. */
@Serializable
object HomeRoute

@Serializable
object ExploreRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int
) {
    Home(HomeRoute, HomeRoute::class, R.string.nav_home, R.drawable.ic_home),
    Explore(ExploreRoute, ExploreRoute::class, R.string.nav_explore, R.drawable.ic_explore)
}

/** Root composable: bottom navigation bar plus the navigation graph. */
@Composable
fun ExpenseShareApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<ExploreRoute> { ExploreScreen() }
        }
    }
}
