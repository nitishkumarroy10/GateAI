package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object VehicleIn : Screen("vehicle_in")
    object VehicleOut : Screen("vehicle_out")
    object VisitorIn : Screen("visitor_in")
    object VisitorOut : Screen("visitor_out")
    object CurrentlyInside : Screen("currently_inside")
    object AiAssistant : Screen("ai_assistant")
    object Reports : Screen("reports")
    object AuditLogs : Screen("audit_logs")
    // Phase 3: Material Movement & Repair
    object MaterialIn : Screen("material_in")
    object MaterialOut : Screen("material_out")
    object MaterialHub : Screen("material_hub")
    object RepairReturn : Screen("repair_return")
    object QrScanner : Screen("qr_scanner/{mode}") {
        fun createRoute(mode: String = "ALL") = "qr_scanner/$mode"
    }
}
