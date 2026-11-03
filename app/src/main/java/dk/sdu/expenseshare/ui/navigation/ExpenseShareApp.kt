package dk.sdu.expenseshare.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.SessionState
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.feature.auth.LoginScreen
import dk.sdu.expenseshare.feature.auth.LoginViewModel
import dk.sdu.expenseshare.feature.auth.RegisterScreen
import dk.sdu.expenseshare.feature.auth.RegisterViewModel
import dk.sdu.expenseshare.feature.auth.SessionUiState
import dk.sdu.expenseshare.feature.auth.SessionViewModel
import dk.sdu.expenseshare.feature.auth.message
import dk.sdu.expenseshare.feature.explore.ExploreScreen
import dk.sdu.expenseshare.feature.home.HomeScreen
import dk.sdu.expenseshare.feature.profile.ProfileScreen
import dk.sdu.expenseshare.feature.profile.ProfileViewModel
import dk.sdu.expenseshare.ui.components.PrimaryButton
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/** Type-safe route definitions for Navigation Compose. */
@Serializable
object LoginRoute

@Serializable
object RegisterRoute

@Serializable
object HomeRoute

@Serializable
object ExploreRoute

@Serializable
object ProfileRoute

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    @DrawableRes val icon: Int
) {
    Home(HomeRoute, HomeRoute::class, R.string.nav_home, R.drawable.ic_home),
    Explore(ExploreRoute, ExploreRoute::class, R.string.nav_explore, R.drawable.ic_explore),
    Profile(ProfileRoute, ProfileRoute::class, R.string.nav_profile, R.drawable.ic_person)
}

/**
 * Root composable. Shows the login/registration flow while signed out and the main app
 * (bottom navigation) while signed in, so signed-out users can never reach the app screens.
 */
@Composable
fun ExpenseShareApp(authRepository: AuthRepository) {
    val sessionViewModel: SessionViewModel =
        viewModel(factory = viewModelFactory { initializer { SessionViewModel(authRepository) } })
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val session = sessionState.session) {
        SessionState.Loading -> SessionStatusScreen(stringResource(R.string.session_loading))
        SessionState.Reconnecting -> SessionStatusScreen(stringResource(R.string.session_reconnecting))
        SessionState.SignedOut -> AuthNavHost(authRepository)
        is SessionState.SignedIn ->
            MainNavHost(
                authRepository = authRepository,
                email = session.email,
                sessionState = sessionState,
                onRetryProfile = sessionViewModel::reloadProfile,
                onProfileUpdated = sessionViewModel::onProfileUpdated,
                onSignOut = sessionViewModel::signOut
            )
    }
}

@Composable
private fun SessionStatusScreen(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProfileLoadErrorScreen(error: AuthErrorReason, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
    }
}

@Composable
private fun AuthNavHost(authRepository: AuthRepository) {
    val navController = rememberNavController()
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LoginRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<LoginRoute> {
                LoginScreen(
                    viewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { LoginViewModel(authRepository) }
                        }
                    ),
                    onNavigateToRegister = {
                        navController.navigate(RegisterRoute) { popUpTo<LoginRoute> { inclusive = true } }
                    }
                )
            }
            composable<RegisterRoute> {
                RegisterScreen(
                    viewModel =
                    viewModel(factory = viewModelFactory { initializer { RegisterViewModel(authRepository) } }),
                    onNavigateToLogin = {
                        navController.navigate(LoginRoute) { popUpTo<RegisterRoute> { inclusive = true } }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainNavHost(
    authRepository: AuthRepository,
    email: String?,
    sessionState: SessionUiState,
    onRetryProfile: () -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    onSignOut: () -> Unit
) {
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
            composable<HomeRoute> {
                HomeScreen(
                    email = email,
                    profile = sessionState.profile,
                    profileError = sessionState.profileError,
                    onRetryProfile = onRetryProfile,
                    onSignOut = onSignOut
                )
            }
            composable<ExploreRoute> { ExploreScreen() }
            composable<ProfileRoute> {
                val profile = sessionState.profile
                if (profile == null) {
                    val error = sessionState.profileError
                    if (error == null) {
                        SessionStatusScreen(stringResource(R.string.profile_loading))
                    } else {
                        ProfileLoadErrorScreen(error = error, onRetry = onRetryProfile)
                    }
                } else {
                    ProfileScreen(
                        viewModel = viewModel(
                            key = "profile-${profile.id}",
                            factory = viewModelFactory {
                                initializer { ProfileViewModel(authRepository, profile, email, onProfileUpdated) }
                            }
                        )
                    )
                }
            }
        }
    }
}
