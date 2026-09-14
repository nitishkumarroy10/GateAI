package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.GateResult
import com.example.data.local.VehicleEntry
import com.example.util.DateTimePickerField
import com.example.viewmodel.GateAiViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleOutScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit
) {
    val insideVehicles by viewModel.insideVehicles.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf<VehicleEntry?>(null) }
    var closingKmInput by remember { mutableStateOf("") }
    
    var supervisorNotes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completedEntry by remember { mutableStateOf<VehicleEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Exit", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (completedEntry != null) {
            val v = completedEntry!!
            val totalKm = (v.closingKm ?: 0) - v.openingKm
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "EXIT SUCCESSFUL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF047857),
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(v.vehicleNumber, fontWeight = FontWeight.Black, fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Text(
                            "$totalKm KM LOGGED",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFF047857)
                        )
                        if (v.supervisorOverride) {
                            Text(
                                "Supervisor Override: ${v.supervisorNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("done_button"),
                    shape = CircleShape
                ) {
                    Text("DONE", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        } else if (selectedVehicle != null) {
            val v = selectedVehicle!!
            val closingKm = closingKmInput.toIntOrNull()
            val distance = if (closingKm != null) closingKm - v.openingKm else null
            
            // Core Rules
            val isLowerKm = closingKm != null && closingKm < v.openingKm
            val needsOverride = distance != null && distance > 300
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(v.vehicleNumber, fontWeight = FontWeight.Black, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Op: ${v.openingKm}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Driver: ${v.driverName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                SleekTextField(
                    value = closingKmInput,
                    onValueChange = {
                        closingKmInput = it.filter { ch -> ch.isDigit() }
                        errorMessage = null
                    },
                    label = "Closing KM",
                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("input_closing_km")
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLowerKm) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Closing KM cannot be lower than Opening KM (${v.openingKm}).",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else if (needsOverride) {
                    Surface(
                        color = Color(0xFFFFF7ED), // Amber 50
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC2410C), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Supervisor Override",
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFC2410C),
                                    fontSize = 18.sp
                                )
                            }
                            Text(
                                "Distance is $distance KM (Limit: 300 KM). Provide a reason to override.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9A3412),
                                fontWeight = FontWeight.Medium
                            )
                            
                            SleekTextField(
                                value = supervisorNotes,
                                onValueChange = { supervisorNotes = it },
                                label = "Override Reason",
                                modifier = Modifier.testTag("input_supervisor_notes")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))

                val isReady = closingKmInput.isNotBlank() && !isLowerKm && (!needsOverride || supervisorNotes.isNotBlank())
                
                Button(
                    onClick = {
                        viewModel.vehicleOut(
                            entryId = v.entryId,
                            closingKm = closingKm ?: v.openingKm,
                            outTime = System.currentTimeMillis(),
                            supervisorOverride = needsOverride,
                            supervisorNotes = supervisorNotes,
                        ) { result ->
                            when (result) {
                                is GateResult.Success -> {
                                    completedEntry = result.data
                                }
                                is GateResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("submit_vehicle_out"),
                    enabled = isReady,
                    shape = CircleShape
                ) {
                    Text("AUTHORIZE EXIT", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            // Vehicle List view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                SleekTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search Vehicle...",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("search_vehicle_out")
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                val filtered = if (searchQuery.isBlank()) {
                    insideVehicles
                } else {
                    insideVehicles.filter {
                        it.vehicleNumber.contains(searchQuery, ignoreCase = true) ||
                        it.driverName.contains(searchQuery, ignoreCase = true)
                    }
                }
                
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active vehicles found inside.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered) { v ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedVehicle = v }
                                    .testTag("vehicle_item_${v.vehicleNumber}"),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(v.vehicleNumber, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(v.driverName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
