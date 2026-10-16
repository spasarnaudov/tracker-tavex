package io.github.spasarnaudov.trackertavex.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.spasarnaudov.trackertavex.core.auth.SessionExpiryViewModel
import io.github.spasarnaudov.trackertavex.core.model.Asset
import io.github.spasarnaudov.trackertavex.feature.account.AccountScreen
import io.github.spasarnaudov.trackertavex.feature.account.ChangePasswordScreen
import io.github.spasarnaudov.trackertavex.feature.account.DeleteAccountScreen
import io.github.spasarnaudov.trackertavex.feature.admin.AdminLogsScreen
import io.github.spasarnaudov.trackertavex.feature.admin.AdminUsersScreen
import io.github.spasarnaudov.trackertavex.feature.admin.LogDetailScreen
import io.github.spasarnaudov.trackertavex.feature.assets.AssetDetailScreen
import io.github.spasarnaudov.trackertavex.feature.portfolio.AssetPurchasesScreen
import io.github.spasarnaudov.trackertavex.feature.assets.AssetsScreen
import io.github.spasarnaudov.trackertavex.feature.charts.ChartsScreen
import io.github.spasarnaudov.trackertavex.feature.connection.ConnectionSettingsScreen
import io.github.spasarnaudov.trackertavex.feature.login.LoginScreen
import io.github.spasarnaudov.trackertavex.feature.portfolio.ManualItemEditScreen
import io.github.spasarnaudov.trackertavex.feature.portfolio.PortfolioScreen
import io.github.spasarnaudov.trackertavex.feature.portfolio.PortfolioViewModel
import io.github.spasarnaudov.trackertavex.feature.register.RegisterScreen
import io.github.spasarnaudov.trackertavex.feature.splash.SplashScreen

private data class MainTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val regularTabs = listOf(
    MainTab(Destinations.PORTFOLIO_GRAPH, "Portfolio", Icons.Filled.PieChart),
    MainTab(Destinations.CHARTS, "Charts", Icons.Filled.BarChart),
    MainTab(Destinations.ACCOUNT, "Account", Icons.Filled.AccountCircle),
)

private val adminTabs = listOf(
    MainTab(Destinations.ADMIN_USERS, "Users", Icons.Filled.Group),
    MainTab(Destinations.ADMIN_LOGS, "Logs", Icons.AutoMirrored.Filled.Article),
    MainTab(Destinations.ACCOUNT, "Account", Icons.Filled.AccountCircle),
)

private val bottomBarRoutes = (regularTabs + adminTabs).map { it.route }.toSet() + setOf(Destinations.PORTFOLIO)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    val sessionExpiryViewModel: SessionExpiryViewModel = hiltViewModel()
    val currentUser by sessionExpiryViewModel.currentUser.collectAsState()
    val mainTabs = if (currentUser?.isAdmin == true) adminTabs else regularTabs
    fun homeDestination() =
        if (sessionExpiryViewModel.currentUser.value?.isAdmin == true) Destinations.ADMIN_USERS else Destinations.PORTFOLIO_GRAPH

    LaunchedEffect(Unit) {
        sessionExpiryViewModel.events.collect {
            navController.navigate(Destinations.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    mainTabs.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.SPLASH,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destinations.SPLASH) {
                SplashScreen(
                    onSessionRestored = {
                        navController.navigate(homeDestination()) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
                    },
                    onNeedsLogin = {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(Destinations.SPLASH) { inclusive = true }
                        }
                    },
                )
            }

            composable(Destinations.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(homeDestination()) {
                            popUpTo(Destinations.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Destinations.REGISTER) },
                    onNavigateToConnectionSettings = { navController.navigate(Destinations.CONNECTION_SETTINGS) },
                )
            }

            composable(Destinations.REGISTER) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(homeDestination()) {
                            popUpTo(Destinations.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Destinations.CONNECTION_SETTINGS) {
                ConnectionSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSessionCleared = {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            navigation(startDestination = Destinations.PORTFOLIO, route = Destinations.PORTFOLIO_GRAPH) {
                composable(Destinations.PORTFOLIO) {
                    PortfolioScreen(
                        onAddManualItem = { navController.navigate(Destinations.manualItemEdit(null)) },
                        onEditManualItem = { key -> navController.navigate(Destinations.manualItemEdit(key)) },
                        onOpenPurchases = { assetId, symbol, name ->
                            navController.navigate(Destinations.assetPurchases(assetId, symbol, name))
                        },
                    )
                }
                composable(
                    route = "${Destinations.MANUAL_ITEM_EDIT}?${Destinations.MANUAL_ITEM_EDIT_ARG}={${Destinations.MANUAL_ITEM_EDIT_ARG}}",
                    arguments = listOf(navArgument(Destinations.MANUAL_ITEM_EDIT_ARG) { type = NavType.StringType; defaultValue = "" }),
                ) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Destinations.PORTFOLIO_GRAPH) }
                    val sharedViewModel: PortfolioViewModel = hiltViewModel(parentEntry)
                    val rawKey = backStackEntry.arguments?.getString(Destinations.MANUAL_ITEM_EDIT_ARG)
                    val clientKey = rawKey?.takeIf { it.isNotBlank() }
                    ManualItemEditScreen(
                        clientKey = clientKey,
                        portfolioViewModel = sharedViewModel,
                        onDone = { navController.popBackStack() },
                    )
                }
            }

            composable(
                route = "${Destinations.ASSET_PURCHASES}/{${Destinations.ASSET_PURCHASES_ID_ARG}}/{${Destinations.ASSET_PURCHASES_SYMBOL_ARG}}/{${Destinations.ASSET_PURCHASES_NAME_ARG}}",
                arguments = listOf(
                    navArgument(Destinations.ASSET_PURCHASES_ID_ARG) { type = NavType.StringType },
                    navArgument(Destinations.ASSET_PURCHASES_SYMBOL_ARG) { type = NavType.StringType },
                    navArgument(Destinations.ASSET_PURCHASES_NAME_ARG) { type = NavType.StringType },
                ),
            ) {
                AssetPurchasesScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Destinations.ASSETS) {
                AssetsScreen(
                    onAssetClick = { asset: Asset ->
                        navController.navigate(Destinations.assetDetail(asset.id, asset.symbol, asset.name))
                    },
                )
            }

            composable(
                route = "${Destinations.ASSET_DETAIL}/{${Destinations.ASSET_DETAIL_ID_ARG}}/{${Destinations.ASSET_DETAIL_SYMBOL_ARG}}/{${Destinations.ASSET_DETAIL_NAME_ARG}}",
                arguments = listOf(
                    navArgument(Destinations.ASSET_DETAIL_ID_ARG) { type = NavType.StringType },
                    navArgument(Destinations.ASSET_DETAIL_SYMBOL_ARG) { type = NavType.StringType },
                    navArgument(Destinations.ASSET_DETAIL_NAME_ARG) { type = NavType.StringType },
                ),
            ) {
                AssetDetailScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Destinations.CHARTS) {
                ChartsScreen()
            }

            composable(Destinations.ACCOUNT) {
                AccountScreen(
                    onLoggedOut = {
                        navController.navigate(Destinations.LOGIN) { popUpTo(0) { inclusive = true } }
                    },
                    onChangePassword = { navController.navigate(Destinations.CHANGE_PASSWORD) },
                    onDeleteAccount = { navController.navigate(Destinations.DELETE_ACCOUNT) },
                    onConnectionSettings = { navController.navigate(Destinations.CONNECTION_SETTINGS) },
                )
            }

            composable(Destinations.CHANGE_PASSWORD) {
                ChangePasswordScreen(
                    onDone = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Destinations.DELETE_ACCOUNT) {
                DeleteAccountScreen(
                    onAccountDeleted = { navController.navigate(Destinations.LOGIN) { popUpTo(0) { inclusive = true } } },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Destinations.ADMIN_USERS) {
                AdminUsersScreen()
            }

            composable(Destinations.ADMIN_LOGS) {
                AdminLogsScreen(
                    onLogClick = { logFile -> navController.navigate(Destinations.adminLogDetail(logFile.name)) },
                )
            }

            composable(
                route = "${Destinations.ADMIN_LOG_DETAIL}/{${Destinations.ADMIN_LOG_DETAIL_ARG}}",
                arguments = listOf(navArgument(Destinations.ADMIN_LOG_DETAIL_ARG) { type = NavType.StringType }),
            ) {
                LogDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
