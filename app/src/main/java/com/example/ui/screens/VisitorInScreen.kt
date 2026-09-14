package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.GateResult
import com.example.data.local.VisitorEntry
import com.example.util.printer.BluetoothPrinterManager
import com.example.util.printer.PrintTemplates
import com.example.viewmodel.GateAiViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorInScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    val sharedPrefs = remember { context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE) }

    var visitorName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("Official Meeting") }
    
    var showOptionalFields by remember { mutableStateOf(false) }
    var vehicleNumber by remember { mutableStateOf("") }
    
    var inDateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val purposes = listOf("Official Meeting", "Delivery / Dispatch", "Interview", "Maintenance", "Audit", "Personal")
    var expandedPurpose by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successPass by remember { mutableStateOf<VisitorEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visitor Entry", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
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
        if (successPass != null) {
            val pass = successPass!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                    "PASS GENERATED",
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = Color.White, modifier = Modifier.size(80.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(pass.visitorName, fontWeight = FontWeight.Black, fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        Text(pass.company, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Host", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pass.host, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pass ID", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pass.passId.take(8).uppercase(), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        val mac = sharedPrefs.getString("printer_mac", null)
                        if (mac != null) {
                            coroutineScope.launch {
                                val connected = printerManager.connect(mac)
                                if (connected) {
                                    val passBytes = PrintTemplates.generateVisitorPass(
                                        passId = pass.passId,
                                        visitorName = pass.visitorName,
                                        host = pass.host,
                                        time = pass.inTime
                                    )
                                    val printed = printerManager.print(passBytes)
                                    if (printed) {
                                        Toast.makeText(context, "Pass Printed Successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Print failed", Toast.LENGTH_SHORT).show()
                                    }
                                    printerManager.disconnect()
                                } else {
                                    Toast.makeText(context, "Printer not connected", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please select a printer in settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("🖨️ PRINT PASS", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it.take(10) },
                    label = "Mobile Number",
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.testTag("input_mobile")
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                SleekTextField(
                    value = visitorName,
                    onValueChange = { visitorName = it },
                    label = "Visitor Name",
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.testTag("input_visitor_name")
                )

                Spacer(modifier = Modifier.height(16.dp))

                SleekTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = "Company / From",
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.testTag("input_company")
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                SleekTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = "Host / Department",
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    modifier = Modifier.testTag("input_host")
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(
                    onClick = { showOptionalFields = !showOptionalFields },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (showOptionalFields) "Hide Advanced Options" else "Advanced Options",
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
                        ExposedDropdownMenuBox(
                            expanded = expandedPurpose,
                            onExpandedChange = { expandedPurpose = it }
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
                                purposes.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = {
                                            purpose = p
                                            expandedPurpose = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        SleekTextField(
                            value = vehicleNumber,
                            onValueChange = { vehicleNumber = it.uppercase() },
                            label = "Visitor Vehicle Number",
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val isValid = mobileNumber.length == 10 && visitorName.isNotBlank() && company.isNotBlank() && host.isNotBlank()

                Button(
                    onClick = {
                        viewModel.visitorIn(
                            visitorName = visitorName,
                            mobileNumber = mobileNumber,
                            company = company,
                            host = host,
                            purpose = purpose,
                            vehicleNumber = vehicleNumber,
                            inTime = inDateTime,
                        ) { result ->
                            when (result) {
                                is GateResult.Success -> {
                                    successPass = result.data
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
                        .testTag("submit_visitor_in"),
                    enabled = isValid,
                    shape = CircleShape
                ) {
                    Text("GENERATE PASS", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
