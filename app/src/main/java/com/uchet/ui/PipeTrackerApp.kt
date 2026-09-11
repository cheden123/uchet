package com.uchet.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uchet.ui.bha.BhaScreen
import com.uchet.ui.editrun.EditRunScreen
import com.uchet.ui.measure.MeasureScreen
import com.uchet.ui.navigation.Destinations
import com.uchet.ui.settings.SettingsScreen
import com.uchet.ui.spopipes.SpoPipesScreen
import com.uchet.ui.sposelect.SpoSelectScreen
import com.uchet.ui.statistics.StatisticsScreen
import com.uchet.util.formatDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipeTrackerApp() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val currentSpo by mainViewModel.currentSpo.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val title = when (currentRoute) {
        Destinations.STATISTICS -> "Статистика"
        Destinations.BHA -> "Компоновка"
        Destinations.SPO_PIPES -> "Трубы СПО"
        Destinations.SETTINGS -> "Настройки"
        Destinations.SELECT_SPO -> "Выбор СПО"
        Destinations.EDIT_RUN -> "Редактирование ряда"
        else -> "Замер"
    }

    fun navigate(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = currentSpo?.title?.ifBlank { "СПО" } ?: "СПО не выбрана",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                currentSpo?.let {
                    Text(
                        text = "${formatDate(it.startDate)} · Куст ${it.wellCluster} · Скв. ${it.wellNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DrawerItem("Замер", Icons.Filled.Straighten) { navigate(Destinations.MEASURE) }
                DrawerItem("Статистика", Icons.Filled.BarChart) { navigate(Destinations.STATISTICS) }
                DrawerItem("Компоновка", Icons.Filled.Tune) { navigate(Destinations.BHA) }
                DrawerItem("Трубы СПО", Icons.Filled.FormatListNumbered) { navigate(Destinations.SPO_PIPES) }
                DrawerItem("Настройки", Icons.Filled.Settings) { navigate(Destinations.SETTINGS) }
                DrawerItem("Выбор СПО", Icons.Filled.SwapHoriz) { navigate(Destinations.SELECT_SPO) }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.MEASURE,
                modifier = Modifier.padding(padding)
            ) {
                composable(Destinations.MEASURE) {
                    MeasureScreen(
                        mainViewModel = mainViewModel,
                        onNavigateToSelectSpo = { navigate(Destinations.SELECT_SPO) }
                    )
                }
                composable(Destinations.STATISTICS) {
                    StatisticsScreen(mainViewModel = mainViewModel)
                }
                composable(Destinations.BHA) {
                    BhaScreen(mainViewModel = mainViewModel)
                }
                composable(Destinations.SPO_PIPES) {
                    SpoPipesScreen(
                        mainViewModel = mainViewModel,
                        onEditRun = { runId -> navController.navigate(Destinations.editRun(runId)) }
                    )
                }
                composable(Destinations.SETTINGS) {
                    SettingsScreen()
                }
                composable(Destinations.SELECT_SPO) {
                    SpoSelectScreen(mainViewModel = mainViewModel)
                }
                composable(
                    route = Destinations.EDIT_RUN,
                    arguments = listOf(navArgument("runId") { type = NavType.LongType })
                ) { entry ->
                    val runId = entry.arguments?.getLong("runId") ?: return@composable
                    EditRunScreen(runId = runId, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
}
