package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.viewmodel.GateAiViewModel
import com.example.ui.screens.*

@Composable
fun GateAiNavGraph(
    navController: NavHostController,
    viewModel: GateAiViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToVehicleIn = { navController.navigate(Screen.VehicleIn.route) },
                onNavigateToVehicleOut = { navController.navigate(Screen.VehicleOut.route) },
                onNavigateToVisitorIn = { navController.navigate(Screen.VisitorIn.route) },
                onNavigateToVisitorOut = { navController.navigate(Screen.VisitorOut.route) },
                onNavigateToMaterialIn = { navController.navigate(Screen.MaterialIn.route) },
                onNavigateToMaterialOut = { navController.navigate(Screen.MaterialOut.route) },
                onNavigateToMaterialHub = { navController.navigate(Screen.MaterialHub.route) },
                onNavigateToCurrentlyInside = { navController.navigate(Screen.CurrentlyInside.route) },
                onNavigateToAiAssistant = { navController.navigate(Screen.AiAssistant.route) },
                onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                onNavigateToAuditLogs = { navController.navigate(Screen.AuditLogs.route) },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.createRoute("ALL")) }
            )
        }
        composable(Screen.VehicleIn.route) {
            VehicleInScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.VehicleOut.route) {
            VehicleOutScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.VisitorIn.route) {
            VisitorInScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.VisitorOut.route) {
            VisitorOutScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.createRoute("VISITOR")) }
            )
        }
        composable(Screen.MaterialIn.route) {
            MaterialInScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MaterialOut.route) {
            MaterialOutScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MaterialHub.route) {
            MaterialHubScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateMaterialIn = { navController.navigate(Screen.MaterialIn.route) },
                onNavigateMaterialOut = { navController.navigate(Screen.MaterialOut.route) },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.createRoute("MATERIAL")) }
            )
        }
        composable(Screen.CurrentlyInside.route) {
            CurrentlyInsideScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AiAssistant.route) {
            AiAssistantScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Reports.route) {
            ReportsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AuditLogs.route) {
            AuditLogsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.QrScanner.route,
            arguments = listOf(androidx.navigation.navArgument("mode") { defaultValue = "ALL" })
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "ALL"
            QrScannerScreen(
                viewModel = viewModel,
                mode = mode,
                onNavigateBack = { navController.popBackStack() },
                onVisitorPassScanned = { passId ->
                    viewModel.setScannedVisitorPass(passId)
                    navController.popBackStack()
                    if (navController.currentDestination?.route != Screen.VisitorOut.route) {
                        navController.navigate(Screen.VisitorOut.route)
                    }
                },
                onMaterialCodeScanned = { code ->
                    viewModel.setScannedMaterialCode(code)
                    navController.popBackStack()
                    if (navController.currentDestination?.route != Screen.MaterialHub.route) {
                        navController.navigate(Screen.MaterialHub.route)
                    }
                },
                onVehicleScanned = { vehicleNumber ->
                    viewModel.setScannedVehicle(vehicleNumber)
                    navController.popBackStack()
                    if (navController.currentDestination?.route != Screen.VehicleOut.route) {
                        navController.navigate(Screen.VehicleOut.route)
                    }
                }
            )
        }
    }
}
