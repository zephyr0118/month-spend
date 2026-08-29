package com.yueji.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yueji.finance.core.database.TransactionListRow
import com.yueji.finance.feature.*
import com.yueji.finance.ui.theme.YueJiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private enum class MainPage(val label: String, val icon: ImageVector) {
    HOME(UiText.home, Icons.Default.Home), TRANSACTIONS(UiText.transactions, Icons.Default.ReceiptLong),
    ANALYTICS(UiText.analytics, Icons.Default.QueryStats), GOALS(UiText.goals, Icons.Default.Flag), SETTINGS(UiText.settings, Icons.Default.Person),
}

@Composable
fun YueJiApp() {
    val main: MainViewModel = hiltViewModel()
    val settings by main.settings.collectAsStateWithLifecycle()
    YueJiTheme(settings.themeMode, settings.dynamicColor) {
        if (!settings.onboardingComplete) {
            val onboarding: OnboardingViewModel = hiltViewModel()
            val state by onboarding.state.collectAsStateWithLifecycle()
            OnboardingScreen(state, onboarding::choose, onboarding::fiscalMonth, onboarding::goals, onboarding::next, onboarding::back, onboarding::finish)
        } else MainShell(main)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MainShell(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val destination by navController.currentBackStackEntryAsState()
    val page = MainPage.entries.firstOrNull { it.name == destination?.destination?.route } ?: MainPage.HOME
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransactionListRow?>(null) }
    var initialCategoryId by remember { mutableStateOf<String?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    val appState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is UserMessage.Error -> snackbar.showSnackbar(message.text)
                is UserMessage.Info -> {
                    if (message.undoTransactionId != null) {
                        val result = snackbar.showSnackbar(message.text, "撤销", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) when (message.undoMode) {
                            UndoMode.DELETE -> viewModel.deleteTransaction(message.undoTransactionId)
                            UndoMode.RESTORE -> viewModel.undoDelete(message.undoTransactionId)
                            null -> Unit
                        }
                    } else snackbar.showSnackbar(message.text)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(18.dp)); Text("月迹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp)); MainPage.entries.forEach { item ->
                    NavigationRailItem(page == item, { navController.navigate(item.name) { launchSingleTop = true; popUpTo(MainPage.HOME.name) { saveState = true }; restoreState = true } }, { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { MainPage.entries.forEach { item -> NavigationBarItem(page == item, { navController.navigate(item.name) { launchSingleTop = true; popUpTo(MainPage.HOME.name) { saveState = true }; restoreState = true } }, { Icon(item.icon, item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)) } } },
                floatingActionButton = {
                    if (page != MainPage.SETTINGS) ExtendedFloatingActionButton(
                        onClick = { editing = null; initialCategoryId = null; showEditor = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("记一笔", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NavHost(navController, startDestination = MainPage.HOME.name) {
                        composable(MainPage.HOME.name) {
                            DashboardScreen(
                                viewModel,
                                onQuickAdd = { categoryId -> editing = null; initialCategoryId = categoryId; showEditor = true },
                                onEditBudget = { budgetDialog = true },
                                onEditTransaction = { row -> editing = row; initialCategoryId = null; showEditor = true },
                            )
                        }
                        composable(MainPage.TRANSACTIONS.name) { TransactionsScreen(viewModel) { editing = it; initialCategoryId = null; showEditor = true } }
                        composable(MainPage.ANALYTICS.name) { AnalyticsScreen(viewModel) }
                        composable(MainPage.GOALS.name) { GoalsScreen(viewModel) }
                        composable(MainPage.SETTINGS.name) { SettingsScreen(viewModel) }
                    }
                }
            }
        }
    }
    if (showEditor) TransactionEditorSheet(viewModel, editing, initialCategoryId, onDismiss = { showEditor = false; editing = null; initialCategoryId = null })
    if (budgetDialog) BudgetDialog(appState.dashboard, { budgetDialog = false }) { amount, mode ->
        viewModel.saveMonthlyBudget(amount, mode); budgetDialog = false
    }
}
