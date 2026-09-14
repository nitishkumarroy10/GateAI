package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Alert
import com.example.data.sync.ConnectionStatus
import com.example.util.DateTimeUtils
import com.example.util.StandardInOutDisplay
import com.example.viewmodel.AppLanguage
import com.example.viewmodel.GateAiViewModel
import com.example.viewmodel.UserRole
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: GateAiViewModel,
    onNavigateToVehicleIn: () -> Unit,
    onNavigateToVehicleOut: () -> Unit,
    onNavigateToVisitorIn: () -> Unit,
    onNavigateToVisitorOut: () -> Unit,
    onNavigateToMaterialIn: () -> Unit,
    onNavigateToMaterialOut: () -> Unit,
    onNavigateToMaterialHub: () -> Unit,
    onNavigateToCurrentlyInside: () -> Unit,
    onNavigateToAiAssistant: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAuditLogs: () -> Unit,
    onNavigateToQrScanner: () -> Unit = {}
) {
    val insideVehicles by viewModel.insideVehicles.collectAsStateWithLifecycle()
    val insideVisitors by viewModel.insideVisitors.collectAsStateWithLifecycle()
    val allVehicles by viewModel.allVehicles.collectAsStateWithLifecycle()
    val allVisitors by viewModel.allVisitors.collectAsStateWithLifecycle()
    val allMovements by viewModel.allMovements.collectAsStateWithLifecycle()
    val currentlyOutside by viewModel.currentlyOutside.collectAsStateWithLifecycle()
    val pendingRepairs by viewModel.pendingRepairs.collectAsStateWithLifecycle()
    val alerts by viewModel.activeAlerts.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val currentUserName by viewModel.currentUserName.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val syncInfo by viewModel.syncInfo.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var showRoleMenu by remember { mutableStateOf(false) }
    var showDemoDialog by remember { mutableStateOf(false) }

    val todayCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = todayCalendar.timeInMillis

    val isHindi = language == AppLanguage.HINDI
    val indianDateStr = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())

    // Metrics calculated strictly from database
    val vehiclesInToday = allVehicles.count { it.inTime >= todayStart }
    val vehiclesOutToday = allVehicles.count { it.outTime != null && it.outTime!! >= todayStart }
    val visitorsInToday = allVisitors.count { it.inTime >= todayStart }
    val visitorsOutToday = allVisitors.count { it.outTime != null && it.outTime!! >= todayStart }
    val materialsInToday = allMovements.count { it.movementType == "IN" && it.created_at >= todayStart }
    val materialsOutToday = allMovements.count { it.movementType == "OUT" && it.created_at >= todayStart }
    val totalKmToday = allVehicles
        .filter { it.closingKm != null && it.outTime != null && it.outTime!! >= todayStart }
        .sumOf { (it.closingKm ?: 0) - it.openingKm }

    val hasDemoRecords = allVehicles.any { it.isDemo } || allVisitors.any { it.isDemo } || allMovements.any { it.isDemo }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "GateAI",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = when (currentRole) {
                                        UserRole.GUARD -> MaterialTheme.colorScheme.primaryContainer
                                        UserRole.SUPERVISOR -> MaterialTheme.colorScheme.secondaryContainer
                                        UserRole.ADMIN -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = currentRole.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (currentRole) {
                                            UserRole.GUARD -> MaterialTheme.colorScheme.onPrimaryContainer
                                            UserRole.SUPERVISOR -> MaterialTheme.colorScheme.onSecondaryContainer
                                            UserRole.ADMIN -> MaterialTheme.colorScheme.onErrorContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Delhi Hub • Gate 1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // QR / Barcode Scanner Icon
                        IconButton(
                            onClick = onNavigateToQrScanner,
                            modifier = Modifier.testTag("btn_dashboard_qr_scanner")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR / Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // AI Assistant Icon
                        IconButton(
                            onClick = onNavigateToAiAssistant,
                            modifier = Modifier.testTag("btn_ai_assistant")
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI Assistant",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Role Selector Menu
                        Box {
                            IconButton(
                                onClick = { showRoleMenu = true },
                                modifier = Modifier.testTag("btn_role_menu")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "User Role")
                            }
                            DropdownMenu(
                                expanded = showRoleMenu,
                                onDismissRequest = { showRoleMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Guard Role (Fast Entry)") },
                                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                    onClick = {
                                        viewModel.setRole(UserRole.GUARD)
                                        showRoleMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supervisor Role (Approvals)") },
                                    leadingIcon = { Icon(Icons.Default.SupervisorAccount, contentDescription = null) },
                                    onClick = {
                                        viewModel.setRole(UserRole.SUPERVISOR)
                                        showRoleMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Admin Role (Full System)") },
                                    leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                    onClick = {
                                        viewModel.setRole(UserRole.ADMIN)
                                        showRoleMenu = false
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isHindi) "Switch to English" else "हिंदी में बदलें (Hindi)") },
                                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                                    onClick = {
                                        viewModel.toggleLanguage()
                                        showRoleMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (hasDemoRecords) "Clear Demo Records" else "Seed Demo Records") },
                                    leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) },
                                    onClick = {
                                        showRoleMenu = false
                                        showDemoDialog = true
                                    }
                                )
                            }
                        }
                    }
                )

                // Sub-header Bar: Indian Date, Sync State, Guard Name
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$indianDateStr  •  $currentUserName",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (syncInfo.status) {
                                            ConnectionStatus.ONLINE -> Color(0xFF10B981)
                                            ConnectionStatus.SYNCING -> Color(0xFFF59E0B)
                                            ConnectionStatus.OFFLINE -> Color(0xFFEF4444)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (syncInfo.status) {
                                    ConnectionStatus.ONLINE -> "Online"
                                    ConnectionStatus.SYNCING -> "Syncing..."
                                    ConnectionStatus.OFFLINE -> "Offline"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (hasDemoRecords) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "DEMO MODE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                // Quick Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_search_input"),
                    placeholder = {
                        Text(
                            if (isHindi) "गाड़ी नंबर, इनवॉइस, माल, पास खोजें..." else "Search Vehicle, Invoice, Material, Pass ID..."
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(
                                onClick = onNavigateToQrScanner,
                                modifier = Modifier.testTag("btn_search_qr_scanner")
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Quick Search Results
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        "Search Results (${searchResults.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (searchResults.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                "No active records matching \"$searchQuery\"",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(searchResults) { result ->
                        SearchResultCard(
                            result = result,
                            onAction = {
                                when (result.type) {
                                    "VEHICLE" -> if (result.status == "Inside") onNavigateToVehicleOut()
                                    "VISITOR" -> if (result.status == "Inside") onNavigateToVisitorOut()
                                    "MATERIAL_IN", "MATERIAL_OUT" -> onNavigateToMaterialHub()
                                }
                            }
                        )
                    }
                }
            } else {
            // 1. KPI Cards (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard(
                            title = if (isHindi) "गाड़ियां अंदर" else "Vehicles Inside",
                            value = insideVehicles.size.toString(),
                            icon = Icons.Default.LocalShipping,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = if (isHindi) "आगंतुक अंदर" else "Visitors Inside",
                            value = insideVisitors.size.toString(),
                            icon = Icons.Default.People,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard(
                            title = "Pending Repairs",
                            value = pendingRepairs.size.toString(),
                            icon = Icons.Default.Build,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Active Alerts",
                            value = alerts.size.toString(),
                            icon = Icons.Default.Warning,
                            color = if (alerts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Guard Quick Actions (2x3 Grid)
            item {
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            title = if (isHindi) "गाड़ी अंदर\n(VEHICLE IN)" else "VEHICLE IN",
                            subtitle = "Record Entry",
                            icon = Icons.Default.LocalShipping,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f).testTag("btn_vehicle_in"),
                            onClick = onNavigateToVehicleIn
                        )
                        PrimaryActionButton(
                            title = if (isHindi) "गाड़ी बाहर\n(VEHICLE OUT)" else "VEHICLE OUT",
                            subtitle = "Closing KM & Exit",
                            icon = Icons.Default.ExitToApp,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f).testTag("btn_vehicle_out"),
                            onClick = onNavigateToVehicleOut
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            title = if (isHindi) "आगंतुक प्रवेश\n(VISITOR IN)" else "VISITOR IN",
                            subtitle = "Issue Gate Pass",
                            icon = Icons.Default.PersonAdd,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f).testTag("btn_visitor_in"),
                            onClick = onNavigateToVisitorIn
                        )
                        PrimaryActionButton(
                            title = if (isHindi) "आगंतुक बाहर\n(VISITOR OUT)" else "VISITOR OUT",
                            subtitle = "Scan Pass / Out",
                            icon = Icons.Default.PersonRemove,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f).testTag("btn_visitor_out"),
                            onClick = onNavigateToVisitorOut
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            title = "MATERIAL HUB",
                            subtitle = "IN, OUT & Repairs",
                            icon = Icons.Default.Inventory,
                            containerColor = Color(0xFFE0E7FF),
                            contentColor = Color(0xFF3730A3),
                            modifier = Modifier.weight(1f).testTag("btn_material_hub"),
                            onClick = onNavigateToMaterialHub
                        )
                        PrimaryActionButton(
                            title = "SCAN QR",
                            subtitle = "Pass / Challan",
                            icon = Icons.Default.QrCodeScanner,
                            containerColor = Color(0xFFDCFCE7),
                            contentColor = Color(0xFF166534),
                            modifier = Modifier.weight(1f).testTag("btn_scan_qr"),
                            onClick = onNavigateToQrScanner
                        )
                    }
                }
            }

            // 3. Secondary Quick Links Row
            item {
                Text(
                    "Tools & Reports",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToCurrentlyInside,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Inside List", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onNavigateToReports,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Reports", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onNavigateToAuditLogs,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Audit Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onNavigateToAiAssistant,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Gate AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Demo Data Management bottom action bar
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Demo Data Management",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.seedDemoData() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load Demo Data")
                            }
                            Button(
                                onClick = { viewModel.clearDemoData() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear Demo Data")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    }

    if (showDemoDialog) {
        AlertDialog(
            onDismissRequest = { showDemoDialog = false },
            title = { Text(if (hasDemoRecords) "Clear Demo Records?" else "Seed Pilot Demo Records?") },
            text = {
                Text(
                    if (hasDemoRecords) {
                        "This will remove all demo vehicles, visitors, material movements, repairs, and alerts without touching real operational data."
                    } else {
                        "This will populate the database with realistic sample trucks, visitors, raw materials, repairs, and alerts for training and evaluation."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hasDemoRecords) {
                            viewModel.clearDemoData()
                        } else {
                            viewModel.seedDemoData()
                        }
                        showDemoDialog = false
                    }
                ) {
                    Text(if (hasDemoRecords) "Clear Demo" else "Seed Demo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun KpiCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrimaryActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = contentColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SummaryMetricRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SearchResultCard(
    result: com.example.viewmodel.UnifiedSearchResult,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (result.type) {
                        "VEHICLE" -> MaterialTheme.colorScheme.primaryContainer
                        "VISITOR" -> MaterialTheme.colorScheme.secondaryContainer
                        "MATERIAL_IN" -> Color(0xFF10B981).copy(alpha = 0.2f)
                        "MATERIAL_OUT" -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val badgeTextColor = when (result.type) {
                        "VEHICLE" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "VISITOR" -> MaterialTheme.colorScheme.onSecondaryContainer
                        "MATERIAL_IN" -> Color(0xFF047857)
                        "MATERIAL_OUT" -> Color(0xFF1D4ED8)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            result.type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(result.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }

                Surface(
                    color = if (result.status == "Inside" || result.status == "SENT_FOR_REPAIR") Color(0xFFF59E0B).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        result.status.uppercase(),
                        color = if (result.status == "Inside" || result.status == "SENT_FOR_REPAIR") Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            StandardInOutDisplay(
                inTime = result.inTime,
                outTime = result.outTime,
                activeLabel = if (result.status == "SENT_FOR_REPAIR") "Under Repair" else "Still Inside"
            )

            if (result.status == "Inside") {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (result.type == "VEHICLE") "PROCESS VEHICLE OUT" else "CHECK OUT VISITOR", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    onDismiss: () -> Unit,
    onView: () -> Unit
) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.severity == "CRITICAL") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (alert.severity == "CRITICAL") MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            alert.type,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (alert.severity == "CRITICAL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            timeFormat.format(Date(alert.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onView,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("VIEW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("DISMISS", fontSize = 12.sp)
                }
            }
        }
    }
}
