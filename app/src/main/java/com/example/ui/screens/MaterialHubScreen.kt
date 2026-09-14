package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.*
import com.example.util.DateTimePickerField
import com.example.util.DateTimeUtils
import com.example.util.StandardInOutDisplay
import com.example.viewmodel.GateAiViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialHubScreen(
    viewModel: GateAiViewModel,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateMaterialIn: () -> Unit,
    onNavigateMaterialOut: () -> Unit,
    onNavigateToQrScanner: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableStateOf(initialTab) }
    val tabTitles = listOf("Currently Outside", "Repairs", "Traceability", "Masters")

    val currentlyOutside by viewModel.currentlyOutside.collectAsStateWithLifecycle()
    val pendingRepairs by viewModel.pendingRepairs.collectAsStateWithLifecycle()
    val allRepairs by viewModel.allRepairs.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val materialsMaster by viewModel.allMaterialsMaster.collectAsStateWithLifecycle()
    val scannedMaterialCode by viewModel.scannedMaterialCode.collectAsStateWithLifecycle()

    var activeTraceabilityQuery by remember { mutableStateOf<String?>(null) }

    // Auto-switch to Traceability tab when material code scanned
    LaunchedEffect(scannedMaterialCode) {
        if (!scannedMaterialCode.isNullOrBlank()) {
            val code = scannedMaterialCode!!
            viewModel.clearScannedMaterialCode()
            selectedTabIndex = 2
            activeTraceabilityQuery = code
        }
    }

    // Return from repair dialog state
    var selectedRepairForReturn by remember { mutableStateOf<RepairRecord?>(null) }

    // Master dialog states
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material Movement Hub", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToQrScanner, modifier = Modifier.testTag("btn_top_scan_barcode")) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode / QR", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateMaterialIn) {
                        Icon(Icons.Default.Login, contentDescription = "Material IN", tint = Color(0xFF10B981))
                    }
                    IconButton(onClick = onNavigateMaterialOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Material OUT", tint = Color(0xFF3B82F6))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            val badgeCount = when (index) {
                                0 -> currentlyOutside.size
                                1 -> pendingRepairs.size
                                else -> null
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                                if (badgeCount != null && badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (index == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(badgeCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> CurrentlyOutsideTab(currentlyOutside, dateFormat)
                1 -> RepairsTab(
                    pendingRepairs = pendingRepairs,
                    allRepairs = allRepairs,
                    dateFormat = dateFormat,
                    onReturnClick = { selectedRepairForReturn = it }
                )
                2 -> TraceabilityTab(
                    viewModel = viewModel,
                    dateFormat = dateFormat,
                    externalQuery = activeTraceabilityQuery,
                    onNavigateToQrScanner = onNavigateToQrScanner
                )
                3 -> MastersTab(
                    suppliers = suppliers,
                    materials = materialsMaster,
                    onAddSupplier = { showAddSupplierDialog = true },
                    onAddMaterial = { showAddMaterialDialog = true }
                )
            }
        }
    }

    // Return From Repair Dialog
    if (selectedRepairForReturn != null) {
        val repair = selectedRepairForReturn!!
        val remainingOutside = (repair.sentQuantity - repair.returnedQuantity).coerceAtLeast(0.0)
        var returnDateTime by remember(repair.repairId) { mutableStateOf(System.currentTimeMillis().coerceAtLeast(repair.outTime)) }
        var returnQty by remember(repair.repairId) { mutableStateOf(remainingOutside.toString()) }
        var condition by remember { mutableStateOf("Good / Tested OK") }
        var returnDocRef by remember { mutableStateOf("") }
        var receivedBy by remember { mutableStateOf("") }
        var returnRemarks by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf<String?>(null) }
        val dialogTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }

        val conditionOptions = listOf("Good / Tested OK", "Repaired with Observations", "Unrepairable / Scrap", "Partially Repaired")

        AlertDialog(
            onDismissRequest = { selectedRepairForReturn = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BuildCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Return From Repair", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Repair ID: ${repair.repairId}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Item: ${repair.materialDescription}", fontWeight = FontWeight.SemiBold)
                            Text("Sent: ${repair.sentQuantity} ${repair.uom} | Already Returned: ${repair.returnedQuantity} ${repair.uom}", style = MaterialTheme.typography.bodySmall)
                            Text("Remaining Outside: $remainingOutside ${repair.uom} with ${repair.repairVendor}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Outward Time: ${dialogTimeFormat.format(Date(repair.outTime))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (repair.assetOrSerial.isNotBlank()) {
                                Text("Asset/Serial: ${repair.assetOrSerial}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (dialogError != null) {
                        Text(dialogError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    // Return Date & Time Picker
                    DateTimePickerField(
                        label = "Return Date & Time *",
                        timestamp = returnDateTime,
                        onTimestampChanged = { returnDateTime = it },
                        minTimestamp = repair.outTime,
                        testTagPrefix = "repair_return_time"
                    )

                    if (returnDateTime < repair.outTime) {
                        Text(
                            "Return time cannot be earlier than outward dispatch time.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = returnQty,
                        onValueChange = { returnQty = it },
                        label = { Text("Actual Returned Quantity (${repair.uom}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    var condExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = condExpanded, onExpandedChange = { condExpanded = it }) {
                        OutlinedTextField(
                            value = condition,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Condition on Return *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = condExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(expanded = condExpanded, onDismissRequest = { condExpanded = false }) {
                            conditionOptions.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { condition = c; condExpanded = false })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = returnDocRef,
                        onValueChange = { returnDocRef = it.uppercase() },
                        label = { Text("Return DC / Challan # *") },
                        placeholder = { Text("e.g. DC-9812") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = receivedBy,
                        onValueChange = { receivedBy = it },
                        label = { Text("Received By (Internal Person) *") },
                        placeholder = { Text("e.g. Maintenance Dept (P. Sharma)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = returnRemarks,
                        onValueChange = { returnRemarks = it },
                        label = { Text("Remarks (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qtyVal = returnQty.toDoubleOrNull() ?: 0.0
                        if (qtyVal <= 0.0) {
                            dialogError = "Returned quantity must be greater than 0."
                            return@Button
                        }
                        if (returnDocRef.isBlank()) {
                            dialogError = "Return Document / Challan is mandatory."
                            return@Button
                        }
                        if (receivedBy.isBlank()) {
                            dialogError = "Received By is mandatory."
                            return@Button
                        }

                        if (returnDateTime < repair.outTime) {
                            dialogError = "Return time cannot be earlier than outward dispatch time."
                            return@Button
                        }

                        viewModel.returnFromRepair(
                            repairId = repair.repairId,
                            actualReturnedQty = qtyVal,
                            condition = condition,
                            returnDocRef = returnDocRef,
                            receivedBy = receivedBy,
                            remarks = returnRemarks,
                            returnTime = returnDateTime
                        ) { res ->
                            when (res) {
                                is GateResult.Success -> {
                                    selectedRepairForReturn = null
                                }
                                is GateResult.Error -> {
                                    dialogError = res.message
                                }
                            }
                        }
                    }
                ) {
                    Text("CONFIRM RETURN")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRepairForReturn = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Supplier Dialog
    if (showAddSupplierDialog) {
        var sName by remember { mutableStateOf("") }
        var sContact by remember { mutableStateOf("") }
        var sMobile by remember { mutableStateOf("") }
        var sGstin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = { Text("Add Supplier Master", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("Supplier Name *") }, singleLine = true)
                    OutlinedTextField(value = sContact, onValueChange = { sContact = it }, label = { Text("Contact Person") }, singleLine = true)
                    OutlinedTextField(value = sMobile, onValueChange = { sMobile = it }, label = { Text("Mobile Number") }, singleLine = true)
                    OutlinedTextField(value = sGstin, onValueChange = { sGstin = it.uppercase() }, label = { Text("GSTIN") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sName.isNotBlank()) {
                            viewModel.addSupplier(
                                Supplier(
                                    supplierName = sName.trim(),
                                    contactPerson = sContact.trim(),
                                    mobile = sMobile.trim(),
                                    gstin = sGstin.trim(),
                                    status = "Active"
                                )
                            )
                            showAddSupplierDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Material Dialog
    if (showAddMaterialDialog) {
        var mName by remember { mutableStateOf("") }
        var mCode by remember { mutableStateOf("") }
        var mCat by remember { mutableStateOf("Raw Material") }
        var mUom by remember { mutableStateOf("NOS") }
        var mIsAsset by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddMaterialDialog = false },
            title = { Text("Add Material Master", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = mName, onValueChange = { mName = it }, label = { Text("Material Name *") }, singleLine = true)
                    OutlinedTextField(value = mCode, onValueChange = { mCode = it.uppercase() }, label = { Text("Item Code") }, singleLine = true)
                    OutlinedTextField(value = mCat, onValueChange = { mCat = it }, label = { Text("Category") }, singleLine = true)
                    OutlinedTextField(value = mUom, onValueChange = { mUom = it.uppercase() }, label = { Text("UOM (NOS, KG, etc.)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mIsAsset, onCheckedChange = { mIsAsset = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Is Fixed Asset / Equipment")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mName.isNotBlank()) {
                            viewModel.addMaterialMaster(
                                MaterialMaster(
                                    materialName = mName.trim(),
                                    itemCode = mCode.trim(),
                                    category = mCat.trim(),
                                    uom = mUom.trim(),
                                    isAsset = mIsAsset
                                )
                            )
                            showAddMaterialDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMaterialDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 1: CURRENTLY OUTSIDE
// -------------------------------------------------------------
@Composable
fun CurrentlyOutsideTab(
    outsideList: List<MaterialMovement>,
    dateFormat: SimpleDateFormat
) {
    if (outsideList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Materials Currently Outside", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("All returnable materials and repairs are accounted for.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        val now = System.currentTimeMillis()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(outsideList) { m ->
                val isOverdue = m.expectedReturnDate != null && m.expectedReturnDate < now
                val daysOutside = (now - m.created_at) / (1000 * 60 * 60 * 24)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m.movementId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (isOverdue) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "OVERDUE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFF3B82F6),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        m.status,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text("Destination: ${m.destinationParty.ifBlank { m.supplierName }}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Reason: ${m.outReason} • Authorized: ${m.authorization}", style = MaterialTheme.typography.bodySmall)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dispatched: ${DateTimeUtils.formatStandardDateTime(m.outTime ?: m.created_at)} ($daysOutside d ago)", style = MaterialTheme.typography.labelSmall)
                            if (m.expectedReturnDate != null) {
                                Text(
                                    "Exp Return: ${DateTimeUtils.formatStandardDate(m.expectedReturnDate!!)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        StandardInOutDisplay(
                            inTime = m.inTime,
                            outTime = m.outTime,
                            activeLabel = "Outside / Return Pending"
                        )

                        if (m.repairId != null) {
                            Text("Linked Repair: ${m.repairId}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: REPAIR MANAGEMENT
// -------------------------------------------------------------
@Composable
fun RepairsTab(
    pendingRepairs: List<RepairRecord>,
    allRepairs: List<RepairRecord>,
    dateFormat: SimpleDateFormat,
    onReturnClick: (RepairRecord) -> Unit
) {
    var showOnlyPending by remember { mutableStateOf(true) }
    val displayed = if (showOnlyPending) pendingRepairs else allRepairs
    val now = System.currentTimeMillis()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (showOnlyPending) "Pending Repairs (${pendingRepairs.size})" else "All Repairs (${allRepairs.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            FilterChip(
                selected = showOnlyPending,
                onClick = { showOnlyPending = !showOnlyPending },
                label = { Text(if (showOnlyPending) "Showing Pending" else "Showing All") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (displayed.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No repair records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayed) { rep ->
                    val remainingOutside = (rep.sentQuantity - rep.returnedQuantity).coerceAtLeast(0.0)
                    val isPendingOrPartial = rep.status == "PENDING" || rep.status == "PARTIALLY_RETURNED"
                    val isOverdue = isPendingOrPartial && rep.expectedReturnDate < now && remainingOutside > 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            else if (rep.status == "RETURNED_FROM_REPAIR") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rep.repairId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                if (isOverdue) {
                                    Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp)) {
                                        Text("OVERDUE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                } else {
                                    Surface(
                                        color = when (rep.status) {
                                            "PENDING" -> Color(0xFFF59E0B)
                                            "PARTIALLY_RETURNED" -> Color(0xFFD97706)
                                            else -> Color(0xFF10B981)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            when (rep.status) {
                                                "PENDING" -> "UNDER REPAIR"
                                                "PARTIALLY_RETURNED" -> "PARTIALLY RETURNED"
                                                else -> "RETURNED ✓"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text("${rep.materialDescription} (${rep.sentQuantity} ${rep.uom})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Vendor: ${rep.repairVendor} • Reason: ${rep.repairReason}", style = MaterialTheme.typography.bodySmall)

                            if (rep.assetOrSerial.isNotBlank()) {
                                Text("Asset / Serial: ${rep.assetOrSerial}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }

                            if (rep.returnedQuantity > 0.0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Returned: ${rep.returnedQuantity} ${rep.uom}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Remaining Outside: $remainingOutside ${rep.uom}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OUT (Sent): ${DateTimeUtils.formatStandardDateTime(rep.sentDate)}", style = MaterialTheme.typography.labelSmall)
                                Text("Exp Return: ${DateTimeUtils.formatStandardDate(rep.expectedReturnDate)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            if (rep.status == "RETURNED_FROM_REPAIR" && (rep.actualReturnDate != null || rep.inTime != null)) {
                                Text(
                                    "IN (Returned): ${DateTimeUtils.formatStandardDateTime(rep.actualReturnDate ?: rep.inTime ?: rep.sentDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            } else {
                                Surface(
                                    color = if (rep.status == "PARTIALLY_RETURNED") Color(0xFFFEF3C7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (rep.status == "PARTIALLY_RETURNED") "IN: Partial Received • $remainingOutside ${rep.uom} Still Outside"
                                        else "IN: Under Repair / Pending Return",
                                        color = Color(0xFFB45309),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (isPendingOrPartial && remainingOutside > 0.0) {
                                Button(
                                    onClick = { onReturnClick(rep) },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (rep.status == "PARTIALLY_RETURNED") "RECEIVE REMAINING RETURN ($remainingOutside ${rep.uom})"
                                        else "RECEIVE RETURN FROM REPAIR",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Returned: ${rep.returnedQuantity} ${rep.uom} on ${rep.actualReturnDate?.let { dateFormat.format(Date(it)) } ?: "N/A"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Condition: ${rep.conditionOnReturn} • Recv By: ${rep.receivedBy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (rep.movementInId != null) {
                                            Text("Linked Inward: ${rep.movementInId}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: TRACEABILITY & INVOICE HISTORY
// -------------------------------------------------------------
@Composable
fun TraceabilityTab(
    viewModel: GateAiViewModel,
    dateFormat: SimpleDateFormat,
    externalQuery: String? = null,
    onNavigateToQrScanner: () -> Unit = {}
) {
    var query by remember { mutableStateOf(externalQuery ?: "") }
    var results by remember { mutableStateOf<List<Pair<MaterialMovement, List<MaterialMovementItem>>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(externalQuery) {
        if (!externalQuery.isNullOrBlank()) {
            query = externalQuery
            isSearching = true
            results = viewModel.searchMaterialHistory(externalQuery)
            isSearching = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Material & Invoice Traceability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("Search by Serial #, Asset #, Invoice #, Challan #, PO # or Material Name", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(10.dp))

        // QR / Barcode Scan Card for Consignment / Challan
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToQrScanner() }
                .testTag("card_scan_consignment_qr"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SCAN MATERIAL / CHALLAN BARCODE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Instant lookup via camera viewfinder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                FilledTonalButton(
                    onClick = onNavigateToQrScanner,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_scan_traceability_barcode")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SCAN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                coroutineScope.launch {
                    isSearching = true
                    results = viewModel.searchMaterialHistory(it)
                    isSearching = false
                }
            },
            label = { Text("Traceability Query") },
            placeholder = { Text("e.g. INV-4587 / SS-200 / CM-00125 / Tata") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            results = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                    IconButton(onClick = onNavigateToQrScanner, modifier = Modifier.testTag("btn_search_bar_scanner")) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Enter a search term above to track invoice or material lifecycle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No material movements matching \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results) { (m, items) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(m.movementId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Surface(
                                    color = if (m.movementType == "IN") Color(0xFF10B981) else Color(0xFF3B82F6),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (m.movementType == "IN") "INWARD" else "OUTWARD",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text("Party / Supplier: ${m.supplierName.ifBlank { m.destinationParty }}", fontWeight = FontWeight.Bold)
                            Text("Vehicle: ${m.vehicleNumber} • Gate: ${m.gate}", style = MaterialTheme.typography.bodySmall)

                            if (m.invoiceNumber.isNotBlank() || m.challanNumber.isNotBlank()) {
                                Text(
                                    "Invoice: ${m.invoiceNumber.ifBlank { "-" }} | Challan: ${m.challanNumber.ifBlank { "-" }} | PO: ${m.poNumber.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text("Items (${items.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            items.forEach { itm ->
                                Text(
                                    "• ${itm.materialDescription} — ${itm.quantity} ${itm.uom}${if (itm.serialNumber.isNotBlank()) " [SN: ${itm.serialNumber}]" else ""}${if (itm.itemCode.isNotBlank()) " [Code: ${itm.itemCode}]" else ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            StandardInOutDisplay(
                                inTime = m.inTime,
                                outTime = m.outTime,
                                activeLabel = if (m.movementType == "IN") "In Yard / Unloading" else "Outside / In Transit"
                            )

                            Text("Logged on ${DateTimeUtils.formatStandardDateTime(m.created_at)} by ${m.recordedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: MASTERS (SUPPLIER & MATERIAL)
// -------------------------------------------------------------
@Composable
fun MastersTab(
    suppliers: List<Supplier>,
    materials: List<MaterialMaster>,
    onAddSupplier: () -> Unit,
    onAddMaterial: () -> Unit
) {
    var subTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("Suppliers (${suppliers.size})") })
                FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("Materials (${materials.size})") })
            }

            IconButton(onClick = if (subTab == 0) onAddSupplier else onAddMaterial) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (subTab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suppliers) { s ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.supplierName, fontWeight = FontWeight.Bold)
                                Text(s.status, color = if (s.status == "Active") Color(0xFF10B981) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (s.contactPerson.isNotBlank() || s.mobile.isNotBlank()) {
                                Text("${s.contactPerson} • ${s.mobile}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (s.gstin.isNotBlank()) {
                                Text("GSTIN: ${s.gstin}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(materials) { m ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m.materialName, fontWeight = FontWeight.Bold)
                                Text(m.uom, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (m.itemCode.isNotBlank()) {
                                    Text("Code: ${m.itemCode}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                                Text("• ${m.category}", style = MaterialTheme.typography.bodySmall)
                                if (m.isAsset) {
                                    Text("• [Fixed Asset]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
