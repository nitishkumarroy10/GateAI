package com.example.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.*
import com.example.util.printer.BluetoothPrinterManager
import com.example.util.printer.PrintTemplates
import com.example.viewmodel.GateAiViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialOutScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    val sharedPrefs = remember { context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE) }

    var destinationParty by remember { mutableStateOf("") }
    var outReason by remember { mutableStateOf("Sales / Dispatch") }
    var referenceDocument by remember { mutableStateOf("") }
    
    val items = remember {
        mutableStateListOf(
            MaterialItemDraft(description = "", quantity = "1", uom = "NOS")
        )
    }

    var showOptionalFields by remember { mutableStateOf(false) }

    var vehicleNumber by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var repairVendor by remember { mutableStateOf("") }
    
    var originalQuantity by remember { mutableStateOf("") }

    val reasonOptions = listOf(
        "Sales / Dispatch",
        "Repair",
        "Repair Return",
        "Return to Supplier",
        "Transfer",
        "Scrap",
        "Other"
    )

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdMovement by remember { mutableStateOf<MaterialMovement?>(null) }
    var createdRepairId by remember { mutableStateOf<String?>(null) }
    var createdItems by remember { mutableStateOf<List<MaterialMovementItem>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material OUT", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
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
        if (createdMovement != null) {
            val m = createdMovement!!
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
                    color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "OUTWARD SUCCESSFUL",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF1D4ED8),
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ID", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.movementId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                        }
                        if (createdRepairId != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Repair ID", color = MaterialTheme.colorScheme.error)
                                Text(createdRepairId!!, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Text(m.destinationParty.ifBlank { m.supplierName }, fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reason:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.outReason, fontWeight = FontWeight.Bold)
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
                                    val passBytes = PrintTemplates.generateMaterialPass(
                                        movementId = m.movementId,
                                        type = "OUTWARD",
                                        party = m.destinationParty.ifBlank { m.supplierName },
                                        itemsCount = createdItems.size,
                                        time = m.inTime
                                    )
                                    val printed = printerManager.print(passBytes)
                                    if (printed) {
                                        Toast.makeText(context, "Challan Printed", Toast.LENGTH_SHORT).show()
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
                    Text("🖨️ PRINT CHALLAN", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
                    Text("DONE", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
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
                    value = destinationParty,
                    onValueChange = { destinationParty = it },
                    label = "Destination / Party Name",
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.testTag("input_destination")
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                SleekTextField(
                    value = referenceDocument,
                    onValueChange = { referenceDocument = it },
                    label = "Ref Doc (Challan/Invoice)",
                    leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    modifier = Modifier.testTag("input_reference_doc")
                )

                Spacer(modifier = Modifier.height(16.dp))

                var reasonExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = reasonExpanded,
                    onExpandedChange = { reasonExpanded = it }
                ) {
                    OutlinedTextField(
                        value = outReason,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Outward Reason", fontWeight = FontWeight.Medium) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded,
                        onDismissRequest = { reasonExpanded = false }
                    ) {
                        reasonOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    outReason = opt
                                    reasonExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ITEMS (${items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            items.add(MaterialItemDraft(quantity = "1", uom = "NOS"))
                        },
                        modifier = Modifier.testTag("add_item_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ADD ITEM", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                items.forEachIndexed { index, itemDraft ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Item 0${index + 1}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            SleekTextField(
                                value = itemDraft.description,
                                onValueChange = { itemDraft.description = it },
                                label = "Description",
                                modifier = Modifier.testTag("item_desc_$index")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SleekTextField(
                                    value = itemDraft.quantity,
                                    onValueChange = { itemDraft.quantity = it },
                                    label = "Qty",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("item_qty_$index")
                                )
                                SleekTextField(
                                    value = itemDraft.uom,
                                    onValueChange = { itemDraft.uom = it },
                                    label = "UOM",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            if (outReason == "Repair Return") {
                                SleekTextField(
                                    value = originalQuantity,
                                    onValueChange = { originalQuantity = it },
                                    label = "Original Inward Qty",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                
                                val orig = originalQuantity.toDoubleOrNull() ?: 0.0
                                val ret = itemDraft.quantity.toDoubleOrNull() ?: 0.0
                                val pending = orig - ret
                                
                                if (pending > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${pending.toInt()} Items Pending",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                            
                            AnimatedVisibility(visible = showOptionalFields) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SleekTextField(
                                        value = itemDraft.serialNumber,
                                        onValueChange = { itemDraft.serialNumber = it },
                                        label = "Serial #",
                                        modifier = Modifier.weight(1f)
                                    )
                                    SleekTextField(
                                        value = itemDraft.assetNumber,
                                        onValueChange = { itemDraft.assetNumber = it },
                                        label = "Asset #",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

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
                        if (outReason == "Repair") {
                            SleekTextField(
                                value = repairVendor,
                                onValueChange = { repairVendor = it },
                                label = "Repair Vendor Details"
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SleekTextField(
                                value = vehicleNumber,
                                onValueChange = { vehicleNumber = it.uppercase() },
                                label = "Vehicle Number",
                                modifier = Modifier.weight(1f)
                            )
                            SleekTextField(
                                value = driverName,
                                onValueChange = { driverName = it },
                                label = "Driver Name",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        if (destinationParty.isBlank()) {
                            errorMessage = "Destination / Party is mandatory."
                            return@Button
                        }
                        if (referenceDocument.isBlank()) {
                            errorMessage = "Reference Document is mandatory."
                            return@Button
                        }

                        val convertedItems = items.mapIndexed { idx, it ->
                            val qtyVal = it.quantity.toDoubleOrNull() ?: 0.0
                            if (it.description.isBlank()) {
                                errorMessage = "Item #${idx + 1}: Description is mandatory."
                                return@Button
                            }
                            if (qtyVal <= 0.0) {
                                errorMessage = "Item #${idx + 1}: Quantity must be greater than 0."
                                return@Button
                            }
                            MaterialMovementItem(
                                movementId = "",
                                materialDescription = it.description.trim(),
                                itemCode = it.itemCode.trim(),
                                quantity = qtyVal,
                                uom = it.uom,
                                serialNumber = it.serialNumber.trim(),
                                assetNumber = it.assetNumber.trim(),
                                remarks = it.remarks.trim()
                            )
                        }

                        val repairDataObj = if (outReason == "Repair") {
                            RepairData(
                                repairVendor = if (repairVendor.isNotBlank()) repairVendor else destinationParty,
                                repairReason = "",
                                assetOrSerial = "",
                                expectedReturnDate = System.currentTimeMillis() + 7 * 24 * 3600 * 1000L,
                                repairChallanRef = referenceDocument,
                                authorizedBy = "",
                                remarks = ""
                            )
                        } else null

                        viewModel.materialOut(
                            destinationParty = destinationParty,
                            outReason = outReason,
                            authorization = "Guard",
                            referenceDocument = referenceDocument,
                            vehicleNumber = vehicleNumber,
                            driverName = driverName,
                            outTime = System.currentTimeMillis(),
                            expectedReturnDate = repairDataObj?.expectedReturnDate,
                            remarks = "",
                            items = convertedItems,
                            repairData = repairDataObj,
                            docAttachment = null
                        ) { res ->
                            when (res) {
                                is GateResult.Success -> {
                                    createdMovement = res.data
                                    createdRepairId = res.data.repairId
                                    createdItems = convertedItems
                                }
                                is GateResult.Error -> {
                                    errorMessage = res.message
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("submit_material_out"),
                    shape = CircleShape
                ) {
                    Text("RECORD OUTWARD", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
