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

data class MaterialItemDraft(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var itemCode: String = "",
    var quantity: String = "1",
    var uom: String = "NOS",
    var serialNumber: String = "",
    var assetNumber: String = "",
    var remarks: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialInScreen(
    viewModel: GateAiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    val sharedPrefs = remember { context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE) }

    var supplierName by remember { mutableStateOf("") }
    var invoiceChallanNumber by remember { mutableStateOf("") }
    
    val items = remember {
        mutableStateListOf(
            MaterialItemDraft(description = "", quantity = "1", uom = "NOS")
        )
    }

    var showOptionalFields by remember { mutableStateOf(false) }

    var vehicleNumber by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var poNumber by remember { mutableStateOf("") }
    var ewayBill by remember { mutableStateOf("") }
    var transporter by remember { mutableStateOf("") }
    var attachDocName by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var inDateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var purpose by remember { mutableStateOf("Production / Delivery") }

    val uomOptions = listOf("NOS", "KG", "BOX", "MTR", "LTR", "SET", "TON")
    val purposeOptions = listOf("Production / Delivery", "Raw Material", "Consumables", "Equipment", "Repair Return", "Capex", "General")

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var createdMovement by remember { mutableStateOf<MaterialMovement?>(null) }
    var createdItems by remember { mutableStateOf<List<MaterialMovementItem>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material IN", fontWeight = FontWeight.Bold, fontSize = 24.sp) },
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
                    "INWARD SUCCESSFUL",
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ID", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.movementId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Text(m.supplierName, fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (m.invoiceNumber.isNotBlank() || m.challanNumber.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ref No:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (m.invoiceNumber.isNotBlank()) m.invoiceNumber else m.challanNumber, fontWeight = FontWeight.Bold)
                            }
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
                                        type = "INWARD",
                                        party = m.supplierName,
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
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = "Supplier / Party Name",
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.testTag("input_supplier_name")
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                SleekTextField(
                    value = invoiceChallanNumber,
                    onValueChange = { invoiceChallanNumber = it.uppercase() },
                    label = "Invoice / Challan Number",
                    leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    modifier = Modifier.testTag("input_invoice_number")
                )

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
                                
                                var uomExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = uomExpanded,
                                    onExpandedChange = { uomExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = itemDraft.uom,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("UOM") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uomExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = Color.Transparent,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = uomExpanded,
                                        onDismissRequest = { uomExpanded = false }
                                    ) {
                                        uomOptions.forEach { u ->
                                            DropdownMenuItem(
                                                text = { Text(u) },
                                                onClick = {
                                                    itemDraft.uom = u
                                                    uomExpanded = false
                                                }
                                            )
                                        }
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
                            value = vehicleNumber,
                            onValueChange = { vehicleNumber = it.uppercase() },
                            label = "Vehicle Number",
                            leadingIcon = { Icon(Icons.Default.LocalShipping, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.testTag("input_vehicle_number")
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SleekTextField(
                                value = poNumber,
                                onValueChange = { poNumber = it.uppercase() },
                                label = "PO Number",
                                modifier = Modifier.weight(1f)
                            )
                            SleekTextField(
                                value = ewayBill,
                                onValueChange = { ewayBill = it.uppercase() },
                                label = "E-Way Bill",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        SleekTextField(
                            value = driverName,
                            onValueChange = { driverName = it },
                            label = "Driver Name"
                        )
                        
                        SleekTextField(
                            value = transporter,
                            onValueChange = { transporter = it },
                            label = "Transporter / Logistics"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        if (supplierName.isBlank()) {
                            errorMessage = "Supplier Name is mandatory."
                            return@Button
                        }
                        if (invoiceChallanNumber.isBlank()) {
                            errorMessage = "Invoice or Challan Number is required."
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

                        viewModel.materialIn(
                            supplierName = supplierName,
                            vehicleNumber = vehicleNumber,
                            invoiceNumber = invoiceChallanNumber,
                            challanNumber = "",
                            poNumber = poNumber,
                            ewayBill = ewayBill,
                            purpose = purpose,
                            driverName = driverName,
                            driverMobile = "",
                            transporter = transporter,
                            receivedBy = "",
                            remarks = remarks,
                            items = convertedItems,
                            docAttachment = null,
                            inTime = inDateTime
                        ) { res ->
                            when (res) {
                                is GateResult.Success -> {
                                    createdMovement = res.data
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
                        .testTag("submit_material_in"),
                    shape = CircleShape
                ) {
                    Text("RECORD INWARD", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
