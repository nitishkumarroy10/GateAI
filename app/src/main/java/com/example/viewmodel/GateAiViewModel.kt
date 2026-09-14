package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GateAiAssistantService
import com.example.data.local.*
import com.example.data.sync.ConnectionStatus
import com.example.data.sync.SyncInfo
import com.example.data.sync.SyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class UserRole {
    GUARD,
    SUPERVISOR,
    ADMIN
}

enum class AppLanguage {
    ENGLISH,
    HINDI
}

data class UnifiedSearchResult(
    val id: String,
    val type: String, // "VEHICLE", "VISITOR", "MATERIAL_IN", "MATERIAL_OUT", "REPAIR"
    val title: String,
    val subtitle: String,
    val inTime: Long,
    val outTime: Long?,
    val status: String,
    val gate: String,
    val rawVehicle: VehicleEntry? = null,
    val rawVisitor: VisitorEntry? = null,
    val rawMovement: MaterialMovement? = null
)

sealed class ScannedEntityResult {
    data class Visitor(
        val entry: VisitorEntry,
        val isInside: Boolean
    ) : ScannedEntityResult()

    data class Material(
        val movement: MaterialMovement,
        val items: List<MaterialMovementItem>,
        val repair: RepairRecord? = null
    ) : ScannedEntityResult()

    data class Repair(
        val record: RepairRecord
    ) : ScannedEntityResult()

    data class Vehicle(
        val entry: VehicleEntry,
        val isInside: Boolean
    ) : ScannedEntityResult()

    data class Unknown(
        val rawCode: String,
        val reason: String = "No matching pass, vehicle, or material consignment found."
    ) : ScannedEntityResult()
}

class GateAiViewModel(
    private val repository: GateAiRepository,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    private val assistantService = GateAiAssistantService()

    // Roles & Session
    private val _currentRole = MutableStateFlow(UserRole.GUARD)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _currentUserName = MutableStateFlow("Raju Sharma (Main Gate)")
    val currentUserName: StateFlow<String> = _currentUserName.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // Sync State
    val syncInfo: StateFlow<SyncInfo> = syncManager?.syncState
        ?: MutableStateFlow(SyncInfo(ConnectionStatus.ONLINE, 0, false, "Local DB Active")).asStateFlow()

    // Real-time Flows from Room (Vehicles & Visitors)
    val insideVehicles: StateFlow<List<VehicleEntry>> = repository.insideVehicles
        .map { list -> list.sortedBy { it.inTime } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insideVisitors: StateFlow<List<VisitorEntry>> = repository.insideVisitors
        .map { list -> list.sortedBy { it.inTime } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVehicles: StateFlow<List<VehicleEntry>> = repository.allVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVisitors: StateFlow<List<VisitorEntry>> = repository.allVisitors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAlerts: StateFlow<List<Alert>> = repository.activeAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 3: Material Flows
    val allMovements: StateFlow<List<MaterialMovement>> = repository.allMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inwardMovements: StateFlow<List<MaterialMovement>> = repository.inwardMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outwardMovements: StateFlow<List<MaterialMovement>> = repository.outwardMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentlyOutside: StateFlow<List<MaterialMovement>> = repository.currentlyOutside
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingRepairs: StateFlow<List<RepairRecord>> = repository.pendingRepairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRepairs: StateFlow<List<RepairRecord>> = repository.allRepairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMaterialsMaster: StateFlow<List<MaterialMaster>> = repository.allMaterialsMaster
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quick Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UnifiedSearchResult>>(emptyList())
    val searchResults: StateFlow<List<UnifiedSearchResult>> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            repository.checkAndTriggerOverdueAlerts()
        }
    }

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            val vResults = repository.searchVehicles(query)
            val visResults = repository.searchVisitors(query)
            val matResults = repository.searchMaterialTraceability(query)

            val unified = mutableListOf<UnifiedSearchResult>()
            vResults.forEach { v ->
                unified.add(
                    UnifiedSearchResult(
                        id = v.entryId,
                        type = "VEHICLE",
                        title = v.vehicleNumber,
                        subtitle = "Driver: ${v.driverName} • ${v.vehicleType}",
                        inTime = v.inTime,
                        outTime = v.outTime,
                        status = v.status,
                        gate = v.gate,
                        rawVehicle = v
                    )
                )
            }
            visResults.forEach { vis ->
                unified.add(
                    UnifiedSearchResult(
                        id = vis.entryId,
                        type = "VISITOR",
                        title = vis.visitorName,
                        subtitle = "${if (vis.company.isNotBlank()) "${vis.company} • " else ""}Meeting: ${vis.host} (Pass #${vis.passId})",
                        inTime = vis.inTime,
                        outTime = vis.outTime,
                        status = vis.status,
                        gate = vis.gate,
                        rawVisitor = vis
                    )
                )
            }
            matResults.forEach { (m, items) ->
                val itemsSummary = items.joinToString(", ") { "${it.materialDescription} (${it.quantity} ${it.uom})" }
                unified.add(
                    UnifiedSearchResult(
                        id = m.movementId,
                        type = if (m.movementType == "IN") "MATERIAL_IN" else "MATERIAL_OUT",
                        title = "${m.movementId} [${m.movementType}] - ${m.supplierName.ifBlank { m.destinationParty }}",
                        subtitle = "Ref: ${m.invoiceNumber.ifBlank { m.challanNumber.ifBlank { m.referenceDocument } }} • Items: $itemsSummary",
                        inTime = m.created_at,
                        outTime = if (m.isReturned) m.updated_at else null,
                        status = m.status,
                        gate = m.gate,
                        rawMovement = m
                    )
                )
            }
            _searchResults.value = unified
        }
    }

    fun setRole(role: UserRole) {
        _currentRole.value = role
        _currentUserName.value = when (role) {
            UserRole.GUARD -> "Raju Sharma (Gate Guard)"
            UserRole.SUPERVISOR -> "Vikram Singh (Supervisor)"
            UserRole.ADMIN -> "Admin (Security Lead)"
        }
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
    }

    // QR & Barcode Scanning Targets
    private val _scannedVisitorPass = MutableStateFlow<String?>(null)
    val scannedVisitorPass: StateFlow<String?> = _scannedVisitorPass.asStateFlow()

    private val _scannedMaterialCode = MutableStateFlow<String?>(null)
    val scannedMaterialCode: StateFlow<String?> = _scannedMaterialCode.asStateFlow()

    private val _scannedVehicleNumber = MutableStateFlow<String?>(null)
    val scannedVehicleNumber: StateFlow<String?> = _scannedVehicleNumber.asStateFlow()

    fun setScannedVisitorPass(passId: String) {
        _scannedVisitorPass.value = passId
    }

    fun clearScannedVisitorPass() {
        _scannedVisitorPass.value = null
    }

    fun setScannedMaterialCode(code: String) {
        _scannedMaterialCode.value = code
    }

    fun clearScannedMaterialCode() {
        _scannedMaterialCode.value = null
    }

    fun setScannedVehicle(number: String) {
        _scannedVehicleNumber.value = number
    }

    fun clearScannedVehicle() {
        _scannedVehicleNumber.value = null
    }

    suspend fun resolveScannedCode(raw: String): ScannedEntityResult {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ScannedEntityResult.Unknown(raw, "Empty code detected.")

        // Extract possible ID if raw string is formatted like "PASS:VP-123456" or "VP-123456" or URL query
        val cleanCandidate = when {
            trimmed.contains("passId=", ignoreCase = true) -> trimmed.substringAfter("passId=").substringBefore("&").substringBefore("}").trim()
            trimmed.startsWith("PASS:", ignoreCase = true) -> trimmed.substringAfter(":").trim()
            trimmed.startsWith("MATERIAL:", ignoreCase = true) -> trimmed.substringAfter(":").trim()
            trimmed.startsWith("CHALLAN:", ignoreCase = true) -> trimmed.substringAfter(":").trim()
            else -> trimmed
        }

        // 1. Check Visitor Pass (by pass ID or mobile)
        val visitorByPass = repository.getVisitorByPassId(cleanCandidate)
        if (visitorByPass != null) {
            val isInside = visitorByPass.status == "Inside"
            return ScannedEntityResult.Visitor(visitorByPass, isInside)
        }

        val insideVis = insideVisitors.value.find {
            it.passId.equals(cleanCandidate, ignoreCase = true) ||
            it.mobileNumber == cleanCandidate ||
            it.visitorName.equals(cleanCandidate, ignoreCase = true)
        }
        if (insideVis != null) {
            return ScannedEntityResult.Visitor(insideVis, isInside = true)
        }

        // 2. Check Material Movement (e.g. MM-XXXX or Movement ID)
        val movementResult = repository.getMovementWithItems(cleanCandidate)
        if (movementResult != null) {
            val (movement, items) = movementResult
            val repair = repository.searchRepairs(movement.movementId).firstOrNull()
            return ScannedEntityResult.Material(movement, items, repair)
        }

        // Check Repair record directly
        val repair = repository.getRepairById(cleanCandidate)
            ?: repository.searchRepairs(cleanCandidate).firstOrNull()
        if (repair != null) {
            return ScannedEntityResult.Repair(repair)
        }

        // Traceability search by invoice, challan, po, or asset
        val matResults = repository.searchMaterialTraceability(cleanCandidate)
        if (matResults.isNotEmpty()) {
            val first = matResults.first()
            return ScannedEntityResult.Material(first.first, first.second, null)
        }

        // 3. Check Vehicle Entry
        val vehicleEntry = repository.getVehicleEntryByIdOrNumber(cleanCandidate)
        if (vehicleEntry != null) {
            return ScannedEntityResult.Vehicle(vehicleEntry, isInside = vehicleEntry.status == "Inside")
        }

        return ScannedEntityResult.Unknown(raw)
    }

    // Vehicle
    fun vehicleIn(
        vehicleNumber: String,
        vehicleType: String,
        driverName: String,
        driverMobile: String = "",
        transporter: String = "",
        purpose: String,
        openingKm: Int,
        inTime: Long = System.currentTimeMillis(),
        poNumber: String = "",
        invoiceNumber: String = "",
        challanNumber: String = "",
        ewayBill: String = "",
        dock: String = "",
        remarks: String = "",
        gate: String = "Main Gate",
        onResult: (GateResult<VehicleEntry>) -> Unit
    ) {
        viewModelScope.launch {
            val entry = VehicleEntry(
                vehicleNumber = vehicleNumber.trim().uppercase(),
                vehicleType = vehicleType,
                driverName = driverName.trim(),
                driverMobile = driverMobile.trim(),
                transporter = transporter.trim(),
                purpose = purpose,
                openingKm = openingKm,
                inTime = inTime,
                poNumber = poNumber.trim(),
                invoiceNumber = invoiceNumber.trim(),
                challanNumber = challanNumber.trim(),
                ewayBill = ewayBill.trim(),
                dock = dock.trim(),
                remarks = remarks.trim(),
                gate = gate,
                recordedBy = _currentUserName.value
            )
            val res = repository.vehicleIn(entry, _currentUserName.value, _currentRole.value.name)
            onResult(res)
        }
    }

    fun vehicleOut(
        entryId: String,
        closingKm: Int,
        outTime: Long = System.currentTimeMillis(),
        supervisorOverride: Boolean = false,
        supervisorNotes: String = "",
        onResult: (GateResult<VehicleEntry>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.vehicleOut(
                entryId = entryId,
                closingKm = closingKm,
                outTime = outTime,
                supervisorOverride = supervisorOverride,
                supervisorNotes = supervisorNotes,
                currentUser = _currentUserName.value,
                currentUserRole = _currentRole.value.name
            )
            onResult(res)
        }
    }

    // Visitor
    fun visitorIn(
        visitorName: String,
        mobileNumber: String,
        company: String = "",
        host: String,
        purpose: String,
        vehicleNumber: String = "",
        inTime: Long = System.currentTimeMillis(),
        gate: String = "Main Gate",
        onResult: (GateResult<VisitorEntry>) -> Unit
    ) {
        viewModelScope.launch {
            val entry = VisitorEntry(
                visitorName = visitorName.trim(),
                mobileNumber = mobileNumber.trim(),
                company = company.trim(),
                host = host.trim(),
                purpose = purpose.trim(),
                vehicleNumber = vehicleNumber.trim().uppercase(),
                inTime = inTime,
                gate = gate,
                recordedBy = _currentUserName.value
            )
            val res = repository.visitorIn(entry, _currentUserName.value, _currentRole.value.name)
            onResult(res)
        }
    }

    fun visitorOut(
        passIdOrId: String,
        outTime: Long = System.currentTimeMillis(),
        onResult: (GateResult<VisitorEntry>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.visitorOut(
                passIdOrId = passIdOrId,
                outTime = outTime,
                currentUser = _currentUserName.value,
                currentUserRole = _currentRole.value.name
            )
            onResult(res)
        }
    }

    // Phase 3: Material Operations
    fun materialIn(
        supplierName: String,
        vehicleNumber: String,
        invoiceNumber: String,
        challanNumber: String = "",
        poNumber: String = "",
        ewayBill: String = "",
        purpose: String,
        driverName: String = "",
        driverMobile: String = "",
        transporter: String = "",
        receivedBy: String = "",
        inTime: Long = System.currentTimeMillis(),
        remarks: String = "",
        gate: String = "Main Gate",
        items: List<MaterialMovementItem>,
        docAttachment: MaterialDocument? = null,
        onResult: (GateResult<MaterialMovement>) -> Unit
    ) {
        viewModelScope.launch {
            val movement = MaterialMovement(
                movementId = "", // Auto-generated
                movementType = "IN",
                supplierName = supplierName.trim(),
                vehicleNumber = vehicleNumber.trim().uppercase(),
                invoiceNumber = invoiceNumber.trim(),
                challanNumber = challanNumber.trim(),
                poNumber = poNumber.trim(),
                ewayBill = ewayBill.trim(),
                purpose = purpose.trim(),
                driverName = driverName.trim(),
                driverMobile = driverMobile.trim(),
                transporter = transporter.trim(),
                receivedBy = receivedBy.trim(),
                remarks = remarks.trim(),
                gate = gate,
                inTime = inTime,
                outTime = inTime,
                created_at = inTime,
                updated_at = inTime,
                recordedBy = _currentUserName.value,
                status = "COMPLETED"
            )
            val res = repository.materialIn(movement, items, docAttachment, _currentUserName.value, _currentRole.value.name)
            onResult(res)
        }
    }

    fun materialOut(
        destinationParty: String,
        outReason: String,
        authorization: String,
        referenceDocument: String,
        vehicleNumber: String = "",
        driverName: String = "",
        outTime: Long = System.currentTimeMillis(),
        expectedReturnDate: Long? = null,
        remarks: String = "",
        gate: String = "Main Gate",
        items: List<MaterialMovementItem>,
        repairData: RepairData? = null,
        docAttachment: MaterialDocument? = null,
        onResult: (GateResult<MaterialMovement>) -> Unit
    ) {
        viewModelScope.launch {
            val movement = MaterialMovement(
                movementId = "", // Auto-generated
                movementType = "OUT",
                destinationParty = destinationParty.trim(),
                outReason = outReason.trim(),
                authorization = authorization.trim(),
                referenceDocument = referenceDocument.trim(),
                vehicleNumber = vehicleNumber.trim().uppercase(),
                driverName = driverName.trim(),
                expectedReturnDate = expectedReturnDate,
                remarks = remarks.trim(),
                gate = gate,
                outTime = outTime,
                inTime = 0L,
                created_at = outTime,
                updated_at = outTime,
                recordedBy = _currentUserName.value
            )
            val res = repository.materialOut(movement, items, repairData, docAttachment, _currentUserName.value, _currentRole.value.name)
            onResult(res)
        }
    }

    fun returnFromRepair(
        repairId: String,
        actualReturnedQty: Double,
        condition: String,
        returnDocRef: String,
        receivedBy: String,
        returnTime: Long = System.currentTimeMillis(),
        remarks: String = "",
        onResult: (GateResult<RepairRecord>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.returnFromRepair(
                repairId = repairId,
                actualReturnedQty = actualReturnedQty,
                condition = condition,
                returnDocRef = returnDocRef,
                receivedBy = receivedBy,
                returnTime = returnTime,
                remarks = remarks,
                currentUser = _currentUserName.value,
                currentUserRole = _currentRole.value.name
            )
            onResult(res)
        }
    }

    suspend fun getMovementItems(movementId: String): List<MaterialMovementItem> {
        return repository.getItemsForMovement(movementId)
    }

    suspend fun searchMaterialHistory(query: String) =
        repository.searchMaterialTraceability(query)

    fun addSupplier(supplier: Supplier) {
        viewModelScope.launch {
            repository.addSupplier(supplier)
        }
    }

    fun addMaterialMaster(material: MaterialMaster) {
        viewModelScope.launch {
            repository.addMaterialMaster(material)
        }
    }

    fun dismissAlert(alert: Alert) {
        viewModelScope.launch {
            repository.dismissAlert(alert, _currentUserName.value, _currentRole.value.name)
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            repository.seedDemoData()
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            repository.clearDemoData()
        }
    }

    suspend fun askAssistant(query: String): String {
        return assistantService.answerQuery(
            query = query,
            vehicles = allVehicles.value,
            visitors = allVisitors.value,
            alerts = activeAlerts.value,
            movements = allMovements.value,
            repairs = allRepairs.value,
            suppliers = allSuppliers.value
        )
    }

    // Reports Generator
    suspend fun generateReport(
        dateRangeDays: Int,
        reportType: String
    ): Pair<String, String> {
        val cal = Calendar.getInstance()
        val endTime: Long
        val startTime: Long

        when (dateRangeDays) {
            0 -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startTime = cal.timeInMillis
                endTime = System.currentTimeMillis()
            }
            1 -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, -1)
                startTime = cal.timeInMillis
                endTime = todayStart - 1
            }
            else -> {
                cal.add(Calendar.DAY_OF_YEAR, -dateRangeDays)
                startTime = cal.timeInMillis
                endTime = System.currentTimeMillis()
            }
        }

        val vehicles = repository.getVehicleEntriesInRange(startTime, endTime)
        val visitors = repository.getVisitorEntriesInRange(startTime, endTime)
        val movements = repository.getMaterialMovementsInRange(startTime, endTime)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

        val csv = StringBuilder()
        val summary = StringBuilder()

        when (reportType) {
            "Daily Gate Register", "Vehicles" -> {
                csv.append("EntryID,VehicleNumber,Type,Driver,Mobile,Transporter,Purpose,OpeningKM,ClosingKM,TotalKM,Gate,InDate,InTime,OutDate,OutTime,TotalDuration,Status,RecordedBy\n")
                vehicles.forEach { v ->
                    val totalKm = if (v.closingKm != null) v.closingKm - v.openingKm else ""
                    val inDate = v.entryDate
                    val inTimeStr = v.entryTime
                    val outDate = v.exitDate ?: ""
                    val outTimeStr = v.exitTime ?: ""
                    val duration = if (v.outTime != null) {
                        com.example.util.DateTimeUtils.formatDuration(v.inTime, v.outTime)
                    } else {
                        com.example.util.DateTimeUtils.formatDuration(v.inTime, System.currentTimeMillis()) + " (Live)"
                    }
                    csv.append("${v.entryId},\"${v.vehicleNumber}\",\"${v.vehicleType}\",\"${v.driverName}\",\"${v.driverMobile}\",\"${v.transporter}\",\"${v.purpose}\",${v.openingKm},${v.closingKm ?: ""},$totalKm,\"${v.gate}\",\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$duration\",\"${v.status}\",\"${v.recordedBy}\"\n")
                }
                val totalDistance = vehicles.filter { it.closingKm != null }.sumOf { (it.closingKm ?: 0) - it.openingKm }
                summary.append("Vehicles Logged: ${vehicles.size}\nCompleted Runs: ${vehicles.count { it.status == "Completed" }}\nCurrently Inside: ${vehicles.count { it.status == "Inside" }}\nTotal Distance: $totalDistance KM")
            }
            "Visitors" -> {
                csv.append("PassID,VisitorName,Mobile,Company,Host,Purpose,VehicleNumber,Gate,InDate,InTime,OutDate,OutTime,TotalDuration,Status,RecordedBy\n")
                visitors.forEach { vis ->
                    val inDate = vis.entryDate
                    val inTimeStr = vis.entryTime
                    val outDate = vis.exitDate ?: ""
                    val outTimeStr = vis.exitTime ?: ""
                    val duration = if (vis.outTime != null) {
                        com.example.util.DateTimeUtils.formatDuration(vis.inTime, vis.outTime)
                    } else {
                        com.example.util.DateTimeUtils.formatDuration(vis.inTime, System.currentTimeMillis()) + " (Live)"
                    }
                    csv.append("\"${vis.passId}\",\"${vis.visitorName}\",\"${vis.mobileNumber}\",\"${vis.company}\",\"${vis.host}\",\"${vis.purpose}\",\"${vis.vehicleNumber}\",\"${vis.gate}\",\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$duration\",\"${vis.status}\",\"${vis.recordedBy}\"\n")
                }
                summary.append("Visitors Logged: ${visitors.size}\nCompleted Visits: ${visitors.count { it.status == "Completed" }}\nCurrently Inside: ${visitors.count { it.status == "Inside" }}")
            }
            "Material Inward" -> {
                csv.append("MovementID,Supplier,VehicleNumber,InvoiceNo,ChallanNo,PONumber,Purpose,Gate,InDate,InTime,OutDate,OutTime,TotalDuration,Status,RecordedBy\n")
                val inMovements = movements.filter { it.movementType == "IN" }
                inMovements.forEach { m ->
                    val inDate = m.entryDate
                    val inTimeStr = m.entryTime
                    val outDate = m.exitDate ?: m.entryDate
                    val outTimeStr = m.exitTime ?: m.entryTime
                    val duration = if (m.outTime != null) com.example.util.DateTimeUtils.formatDuration(m.inTime, m.outTime!!) else "0m"
                    csv.append("\"${m.movementId}\",\"${m.supplierName}\",\"${m.vehicleNumber}\",\"${m.invoiceNumber}\",\"${m.challanNumber}\",\"${m.poNumber}\",\"${m.purpose}\",\"${m.gate}\",\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$duration\",\"${m.status}\",\"${m.recordedBy}\"\n")
                }
                summary.append("Material Inward Entries: ${inMovements.size}\nDistinct Suppliers: ${inMovements.map { it.supplierName }.distinct().size}")
            }
            "Material Outward" -> {
                csv.append("MovementID,DestinationParty,Reason,Authorization,ReferenceDoc,VehicleNumber,ExpectedReturn,InDate,InTime,OutDate,OutTime,TotalDuration,Status,RecordedBy\n")
                val outMovements = movements.filter { it.movementType == "OUT" }
                val now = System.currentTimeMillis()
                outMovements.forEach { m ->
                    val expStr = m.expectedReturnDate?.let { dateFormat.format(Date(it)) } ?: "N/A"
                    val outDate = m.exitDate ?: ""
                    val outTimeStr = m.exitTime ?: ""
                    val inDate = if (m.isReturned && m.inTime > 0) m.entryDate else ""
                    val inTimeStr = if (m.isReturned && m.inTime > 0) m.entryTime else ""
                    val duration = if (m.isReturned && m.inTime > 0 && m.outTime != null) {
                        com.example.util.DateTimeUtils.formatDuration(m.outTime!!, m.inTime)
                    } else if (m.outTime != null) {
                        com.example.util.DateTimeUtils.formatDuration(m.outTime!!, now) + " (Outside)"
                    } else ""
                    csv.append("\"${m.movementId}\",\"${m.destinationParty}\",\"${m.outReason}\",\"${m.authorization}\",\"${m.referenceDocument}\",\"${m.vehicleNumber}\",\"$expStr\",\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$duration\",\"${m.status}\",\"${m.recordedBy}\"\n")
                }
                summary.append("Material Outward Entries: ${outMovements.size}\nUnder Repair / Outside: ${outMovements.count { it.expectedReturnDate != null && !it.isReturned }}")
            }
            "Repairs" -> {
                val repairs = repository.allRepairs.first()
                val now = System.currentTimeMillis()
                csv.append("RepairID,Material,Vendor,SentQty,ReturnedQty,UOM,InDate,InTime,OutDate,OutTime,ExpectedReturn,TotalDuration,Status,Condition\n")
                repairs.forEach { r ->
                    val outDate = r.exitDate
                    val outTimeStr = r.exitTime
                    val inDate = r.entryDate ?: ""
                    val inTimeStr = r.entryTime ?: ""
                    val expStr = dateFormat.format(Date(r.expectedReturnDate))
                    val duration = if (r.inTime != null) {
                        com.example.util.DateTimeUtils.formatDuration(r.outTime, r.inTime!!)
                    } else {
                        com.example.util.DateTimeUtils.formatDuration(r.outTime, now) + " (Pending)"
                    }
                    csv.append("\"${r.repairId}\",\"${r.materialDescription}\",\"${r.repairVendor}\",${r.sentQuantity},${r.returnedQuantity},\"${r.uom}\",\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$expStr\",\"$duration\",\"${r.status}\",\"${r.conditionOnReturn}\"\n")
                }
                summary.append("Total Repairs: ${repairs.size}\nPending Returns: ${repairs.count { it.status == "PENDING" }}\nOverdue Repairs: ${repairs.count { it.status == "PENDING" && it.expectedReturnDate < System.currentTimeMillis() }}")
            }
            "Material Currently Outside" -> {
                val outside = repository.currentlyOutside.first()
                csv.append("MovementID,Destination,Reason,Vehicle,OutDate,OutTime,ExpectedReturn,TotalDuration,DaysOutside,Status\n")
                val now = System.currentTimeMillis()
                outside.forEach { m ->
                    val expStr = m.expectedReturnDate?.let { dateFormat.format(Date(it)) } ?: "N/A"
                    val outDate = m.exitDate ?: ""
                    val outTimeStr = m.exitTime ?: ""
                    val duration = if (m.outTime != null) com.example.util.DateTimeUtils.formatDuration(m.outTime!!, now) else ""
                    val days = if (m.outTime != null) (now - m.outTime!!) / (1000 * 60 * 60 * 24) else (now - m.created_at) / (1000 * 60 * 60 * 24)
                    csv.append("\"${m.movementId}\",\"${m.destinationParty}\",\"${m.outReason}\",\"${m.vehicleNumber}\",\"$outDate\",\"$outTimeStr\",\"$expStr\",\"$duration\",$days,\"${m.status}\"\n")
                }
                summary.append("Currently Outside Consignments: ${outside.size}\nOverdue Returns: ${outside.count { (it.expectedReturnDate ?: Long.MAX_VALUE) < now }}")
            }
            "KM Discrepancies" -> {
                val unusual = vehicles.filter { it.closingKm != null && ((it.closingKm!! - it.openingKm) > 300 || it.closingKm!! < it.openingKm) }
                csv.append("EntryID,VehicleNumber,Driver,OpeningKM,ClosingKM,DifferenceKM,InDate,InTime,OutDate,OutTime,TotalDuration,Status,Override,SupervisorNotes\n")
                unusual.forEach { v ->
                    val diff = (v.closingKm ?: 0) - v.openingKm
                    val inDate = v.entryDate
                    val inTimeStr = v.entryTime
                    val outDate = v.exitDate ?: ""
                    val outTimeStr = v.exitTime ?: ""
                    val duration = if (v.outTime != null) com.example.util.DateTimeUtils.formatDuration(v.inTime, v.outTime) else ""
                    csv.append("${v.entryId},\"${v.vehicleNumber}\",\"${v.driverName}\",${v.openingKm},${v.closingKm ?: ""},$diff,\"$inDate\",\"$inTimeStr\",\"$outDate\",\"$outTimeStr\",\"$duration\",\"${v.status}\",${v.supervisorOverride},\"${v.supervisorNotes}\"\n")
                }
                summary.append("Total KM Discrepancies: ${unusual.size}\nSupervisor Overrides: ${unusual.count { it.supervisorOverride }}")
            }
            else -> {
                csv.append("Summary Report\n")
                summary.append("Vehicles: ${vehicles.size}, Visitors: ${visitors.size}, Movements: ${movements.size}")
            }
        }

        return Pair(summary.toString(), csv.toString())
    }
}
