package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.FarmManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            FarmManagerTheme(
                darkTheme = uiState.isDarkTheme,
                fontScale = uiState.fontScale
            ) {
                FarmManagerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FarmManagerApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val targetScreen = remember {
        (context as? ComponentActivity)?.intent?.getStringExtra("target_screen")
    }

    LaunchedEffect(targetScreen) {
        if (!targetScreen.isNullOrBlank()) {
            navController.navigate(targetScreen)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("home") {
            HomeScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToFarms = { navController.navigate("farms") },
                onNavigateToTasks = { navController.navigate("tasks") },
                onNavigateToIrrigation = { navController.navigate("irrigation") },
                onNavigateToPersons = { navController.navigate("persons") },
                onNavigateToTags = { navController.navigate("tags") },
                onNavigateToWorkforce = { navController.navigate("workforce") },
                onNavigateToExpenses = { navController.navigate("expenses") },
                onNavigateToCrops = { navController.navigate("crops") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToLedger = { navController.navigate("ledger") },
                onNavigateToBackup = { navController.navigate("backup") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("irrigation") {
            IrrigationScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("reports") {
            ReportsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("workforce") {
            WorkforceScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCrewDetail = { crewId ->
                    navController.navigate("crew_detail/$crewId")
                }
            )
        }

        composable("expenses") {
            ExpenseScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("crops") {
            CropsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "crew_detail/{crewId}",
            arguments = listOf(navArgument("crewId") { type = NavType.StringType })
        ) { backStackEntry ->
            val crewId = backStackEntry.arguments?.getString("crewId") ?: ""
            CrewDetailScreen(
                crewId = crewId,
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("ledger") {
            LedgerScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAccountDetail = { accountType, accountId ->
                    navController.navigate("account_detail/$accountType/$accountId")
                }
            )
        }

        composable(
            route = "account_detail/{accountType}/{accountId}",
            arguments = listOf(
                navArgument("accountType") { type = NavType.StringType },
                navArgument("accountId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val accountType = backStackEntry.arguments?.getString("accountType") ?: "person"
            val accountId = backStackEntry.arguments?.getString("accountId") ?: ""
            AccountDetailScreen(
                accountType = accountType,
                accountId = accountId,
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("tasks") {
            TasksScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("farms") {
            FarmsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFarmDetail = { farmId ->
                    navController.navigate("farm_detail/$farmId")
                }
            )
        }

        composable(
            route = "farm_detail/{farmId}",
            arguments = listOf(navArgument("farmId") { type = NavType.StringType })
        ) { backStackEntry ->
            val farmId = backStackEntry.arguments?.getString("farmId") ?: ""
            FarmDetailScreen(
                farmId = farmId,
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("persons") {
            PersonsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("tags") {
            TagsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("backup") {
            BackupScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
