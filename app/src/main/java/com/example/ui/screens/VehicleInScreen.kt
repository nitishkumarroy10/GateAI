package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.GateResult
import com.example.data.local.VehicleEntry
import com.example.viewmodel.GateAiViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleInScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit
) {
    var vehicleNumber by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var openingKm by remember { mutableStateOf("") }

    var showOptionalFields by remember { mutableStateOf(false) }
    var driverMobile by remember { mutableStateOf("") }
    var transporter by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("Truck") }
    var purpose by remember { mutableStateOf("Delivery") }
    
    var poNumber by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var challanNumber by remember { mutableStateOf("") }
    var ewayBill by remember { mutableStateOf("") }
    var dock by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    
    var entryDateTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val vehicleTypes = listOf("Truck", "Container", "Tempo", "Trailer", "Van", "Car", "Bike")
    var expandedType by remember { mutableStateOf(false) }

    val purposes = listOf("Delivery", "Pickup", "Material Movement", "Staff", "Vendor", "Other")
    var expandedPurpose by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successEntry by remember { mutableStateOf<VehicleEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Entry", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
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
        if (successEntry != null) {
            val entry = successEntry!!
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
                    "ENTRY SUCCESSFUL",
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(entry.vehicleNumber, fontWeight = FontWeight.Black, fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Driver", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entry.driverName, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opening KM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${entry.openingKm} km", fontWeight = FontWeight.Bold)
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
                    Text("DONE", fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        } else {
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

                SleekTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it.uppercase() },
                    label = "Vehicle Number",
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.testTag("input_vehicle_number")
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                SleekTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = "Driver Name",
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SleekTextField(
                    value = openingKm,
                    onValueChange = { openingKm = it },
                    label = "Opening KM",
                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Advanced Logistics Toggle
                TextButton(
                    onClick = { showOptionalFields = !showOptionalFields },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (showOptionalFields) "Hide Advanced Logistics" else "Advanced Logistics",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (showOptionalFields) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = showOptionalFields) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        SleekTextField(
                            value = driverMobile,
                            onValueChange = { driverMobile = it },
                            label = "Driver Mobile",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        
                        SleekTextField(
                            value = transporter,
                            onValueChange = { transporter = it },
                            label = "Transporter / Logistics"
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = expandedType,
                                onExpandedChange = { expandedType = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = vehicleType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Vehicle Type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedType,
                                    onDismissRequest = { expandedType = false }
                                ) {
                                    vehicleTypes.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                vehicleType = selectionOption
                                                expandedType = false
                                            }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = expandedPurpose,
                                onExpandedChange = { expandedPurpose = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = purpose,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Purpose") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPurpose) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedPurpose,
                                    onDismissRequest = { expandedPurpose = false }
                                ) {
                                    purposes.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                purpose = selectionOption
                                                expandedPurpose = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SleekTextField(
                                value = poNumber,
                                onValueChange = { poNumber = it },
                                label = "PO #",
                                modifier = Modifier.weight(1f)
                            )
                            SleekTextField(
                                value = invoiceNumber,
                                onValueChange = { invoiceNumber = it },
                                label = "Invoice #",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SleekTextField(
                                value = challanNumber,
                                onValueChange = { challanNumber = it },
                                label = "Challan #",
                                modifier = Modifier.weight(1f)
                            )
                            SleekTextField(
                                value = ewayBill,
                                onValueChange = { ewayBill = it },
                                label = "E-Way Bill",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        SleekTextField(
                            value = dock,
                            onValueChange = { dock = it },
                            label = "Dock Assignment"
                        )

                        SleekTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = "Remarks",
                            singleLine = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val isValid = vehicleNumber.isNotBlank() && driverName.isNotBlank() && openingKm.isNotBlank()

                Button(
                    onClick = {
                        val km = openingKm.toIntOrNull() ?: 0
                        viewModel.vehicleIn(
                            vehicleNumber = vehicleNumber,
                            vehicleType = vehicleType,
                            driverName = driverName,
                            driverMobile = driverMobile,
                            transporter = transporter,
                            purpose = purpose,
                            openingKm = km,
                            inTime = entryDateTime,
                            poNumber = poNumber,
                            invoiceNumber = invoiceNumber,
                            challanNumber = challanNumber,
                            ewayBill = ewayBill,
                            dock = dock,
                            remarks = remarks
                        ) { result ->
                            when (result) {
                                is GateResult.Success -> {
                                    successEntry = result.data
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
                        .testTag("submit_vehicle_in"),
                    enabled = isValid,
                    shape = CircleShape
                ) {
                    Text("CONFIRM ENTRY", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
