package com.example.data.local

import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

sealed class GateResult<out T> {
    data class Success<out T>(val data: T) : GateResult<T>()
    data class Error(val message: String) : GateResult<Nothing>()
}

data class RepairData(
    val repairVendor: String,
    val repairReason: String,
    val assetOrSerial: String = "",
    val expectedReturnDate: Long,
    val repairChallanRef: String = "",
    val authorizedBy: String,
    val remarks: String = ""
)

class GateAiRepository(private val db: GateAiDatabase) {
    // Vehicles & Visitors
    val insideVehicles: Flow<List<VehicleEntry>> = db.vehicleEntryDao().getInsideVehicles()
    val allVehicles: Flow<List<VehicleEntry>> = db.vehicleEntryDao().getAllEntries()
    val insideVisitors: Flow<List<VisitorEntry>> = db.visitorEntryDao().getInsideVisitors()
    val allVisitors: Flow<List<VisitorEntry>> = db.visitorEntryDao().getAllEntries()
    val activeAlerts: Flow<List<Alert>> = db.alertDao().getActiveAlerts()
    val auditLogs: Flow<List<AuditLog>> = db.auditLogDao().getRecentAuditLogs()

    // Phase 3: Materials, Repairs, Suppliers
    val allMovements: Flow<List<MaterialMovement>> = db.materialMovementDao().getAllMovements()
    val inwardMovements: Flow<List<MaterialMovement>> = db.materialMovementDao().getMovementsByType("IN")
    val outwardMovements: Flow<List<MaterialMovement>> = db.materialMovementDao().getMovementsByType("OUT")
    val currentlyOutside: Flow<List<MaterialMovement>> = db.materialMovementDao().getCurrentlyOutside()
    val pendingRepairs: Flow<List<RepairRecord>> = db.repairRecordDao().getPendingRepairs()
    val allRepairs: Flow<List<RepairRecord>> = db.repairRecordDao().getAllRepairs()
    val allSuppliers: Flow<List<Supplier>> = db.supplierDao().getAllSuppliers()
    val allMaterialsMaster: Flow<List<MaterialMaster>> = db.materialMasterDao().getAllMaterials()

    // ==========================================
    // VEHICLE FLOWS
    // ==========================================

    suspend fun vehicleIn(
        entry: VehicleEntry,
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<VehicleEntry> {
        val normalizedNumber = entry.vehicleNumber.trim().uppercase()
        val activeEntry = db.vehicleEntryDao().getActiveEntryForVehicle(normalizedNumber)
        if (activeEntry != null) {
            val alert = Alert(
                type = "Duplicate Entry",
                message = "Attempted IN for vehicle $normalizedNumber which is already inside (since ${formatShortTime(activeEntry.inTime)}).",
                severity = "WARNING",
                relatedId = activeEntry.entryId
            )
            db.alertDao().insertAlert(alert)
            return GateResult.Error("Vehicle $normalizedNumber is already inside! Please complete vehicle OUT before logging entry.")
        }

        val cleanedEntry = entry.copy(
            vehicleNumber = normalizedNumber,
            recordedBy = currentUser
        )

        db.vehicleDao().insertVehicle(
            Vehicle(
                vehicleNumber = cleanedEntry.vehicleNumber,
                vehicleType = cleanedEntry.vehicleType,
                driverName = cleanedEntry.driverName,
                driverMobile = cleanedEntry.driverMobile,
                transporter = cleanedEntry.transporter,
                isDemo = cleanedEntry.isDemo
            )
        )
        db.vehicleEntryDao().insertEntry(cleanedEntry)

        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "VEHICLE_IN",
                recordId = cleanedEntry.entryId,
                newValue = "${cleanedEntry.vehicleNumber} | ${cleanedEntry.driverName} | Opening KM: ${cleanedEntry.openingKm}",
                details = "Gate: ${cleanedEntry.gate} | Type: ${cleanedEntry.vehicleType} | Purpose: ${cleanedEntry.purpose}"
            )
        )

        return GateResult.Success(cleanedEntry)
    }

    suspend fun vehicleOut(
        entryId: String,
        closingKm: Int,
        outTime: Long = System.currentTimeMillis(),
        supervisorOverride: Boolean = false,
        supervisorNotes: String = "",
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<VehicleEntry> {
        val entry = db.vehicleEntryDao().getEntryById(entryId)
            ?: return GateResult.Error("Vehicle entry not found or already completed.")

        if (entry.status != "Inside") {
            return GateResult.Error("Vehicle has already checked out.")
        }

        if (outTime < entry.inTime) {
            return GateResult.Error("Exit date & time (${DateTimeUtils.formatStandardDateTime(outTime)}) cannot be earlier than entry time (${DateTimeUtils.formatStandardDateTime(entry.inTime)}).")
        }

        if (closingKm < entry.openingKm) {
            db.alertDao().insertAlert(
                Alert(
                    type = "KM Error",
                    message = "Vehicle ${entry.vehicleNumber} reported closing KM ($closingKm) lower than opening KM (${entry.openingKm}).",
                    severity = "CRITICAL",
                    relatedId = entry.entryId
                )
            )
            return GateResult.Error("Closing KM ($closingKm) cannot be lower than Opening KM (${entry.openingKm}).")
        }

        val distance = closingKm - entry.openingKm
        val isUnusual = distance > 300

        if (isUnusual && !supervisorOverride) {
            db.alertDao().insertAlert(
                Alert(
                    type = "Unusual KM",
                    message = "Vehicle ${entry.vehicleNumber} has unusually high distance ($distance KM). Opening: ${entry.openingKm}, Closing: $closingKm.",
                    severity = "WARNING",
                    relatedId = entry.entryId
                )
            )
            return GateResult.Error("Unusual KM difference of $distance KM requires Supervisor Override.")
        }

        val updated = entry.copy(
            closingKm = closingKm,
            outTime = outTime,
            status = "Completed",
            supervisorOverride = supervisorOverride,
            supervisorNotes = supervisorNotes
        )
        db.vehicleEntryDao().updateEntry(updated)

        val actionType = if (supervisorOverride) "SUPERVISOR_OVERRIDE" else "VEHICLE_OUT"
        val logDetails = if (supervisorOverride) {
            "Supervisor Override Approved: $supervisorNotes | Distance: $distance KM"
        } else {
            "Normal OUT | Distance: $distance KM"
        }

        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = actionType,
                recordId = entry.entryId,
                oldValue = "Inside (Opening KM: ${entry.openingKm})",
                newValue = "Completed (Closing KM: $closingKm, Distance: $distance KM)",
                details = logDetails
            )
        )

        return GateResult.Success(updated)
    }

    // ==========================================
    // VISITOR FLOWS
    // ==========================================

    suspend fun visitorIn(
        entry: VisitorEntry,
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<VisitorEntry> {
        val cleanMobile = entry.mobileNumber.trim()
        val activeVisitor = db.visitorEntryDao().getActiveEntryByMobile(cleanMobile)
        if (activeVisitor != null) {
            db.alertDao().insertAlert(
                Alert(
                    type = "Duplicate Entry",
                    message = "Visitor ${entry.visitorName} ($cleanMobile) already has an active pass (${activeVisitor.passId}).",
                    severity = "WARNING",
                    relatedId = activeVisitor.entryId
                )
            )
            return GateResult.Error("Visitor with mobile $cleanMobile is already inside with Pass #${activeVisitor.passId}. Check out first.")
        }

        val cleanedEntry = entry.copy(recordedBy = currentUser)

        db.visitorDao().insertVisitor(
            Visitor(
                visitorName = cleanedEntry.visitorName,
                mobileNumber = cleanedEntry.mobileNumber,
                company = cleanedEntry.company,
                isDemo = cleanedEntry.isDemo
            )
        )
        db.visitorEntryDao().insertEntry(cleanedEntry)

        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "VISITOR_IN",
                recordId = cleanedEntry.entryId,
                newValue = "${cleanedEntry.visitorName} | Pass: ${cleanedEntry.passId} | Host: ${cleanedEntry.host}",
                details = "Company: ${cleanedEntry.company} | Purpose: ${cleanedEntry.purpose}"
            )
        )

        return GateResult.Success(cleanedEntry)
    }

    suspend fun visitorOut(
        passIdOrId: String,
        outTime: Long = System.currentTimeMillis(),
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<VisitorEntry> {
        val entry = db.visitorEntryDao().getEntryByPassId(passIdOrId.trim().uppercase())
            ?: return GateResult.Error("Pass not found. Please verify the Pass ID or search by name.")

        if (entry.status != "Inside") {
            return GateResult.Error("Visitor ${entry.visitorName} is already checked out.")
        }

        if (outTime < entry.inTime) {
            return GateResult.Error("Exit date & time (${DateTimeUtils.formatStandardDateTime(outTime)}) cannot be earlier than entry time (${DateTimeUtils.formatStandardDateTime(entry.inTime)}).")
        }

        val updated = entry.copy(
            outTime = outTime,
            status = "Completed"
        )
        db.visitorEntryDao().updateEntry(updated)

        val durationMinutes = (updated.outTime!! - updated.inTime).coerceAtLeast(0L) / (1000 * 60)
        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "VISITOR_OUT",
                recordId = entry.entryId,
                oldValue = "Inside (Pass #${entry.passId})",
                newValue = "Completed (Duration: ${durationMinutes}m)",
                details = "Visitor: ${entry.visitorName} | Host: ${entry.host}"
            )
        )

        return GateResult.Success(updated)
    }

    // ==========================================
    // PHASE 3: MATERIAL INWARD FLOW
    // ==========================================

    suspend fun materialIn(
        movement: MaterialMovement,
        items: List<MaterialMovementItem>,
        docAttachment: MaterialDocument? = null,
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<MaterialMovement> {
        if (movement.supplierName.isBlank()) {
            return GateResult.Error("Supplier Name is mandatory.")
        }
        if (movement.vehicleNumber.isBlank()) {
            return GateResult.Error("Vehicle Number is mandatory.")
        }
        if (movement.invoiceNumber.isBlank() && movement.challanNumber.isBlank()) {
            return GateResult.Error("Invoice or Challan Number is mandatory.")
        }
        if (movement.purpose.isBlank()) {
            return GateResult.Error("Purpose is mandatory.")
        }
        if (items.isEmpty()) {
            return GateResult.Error("At least one material item is required.")
        }
        for (item in items) {
            if (item.materialDescription.isBlank()) {
                return GateResult.Error("Material description cannot be empty.")
            }
            if (item.quantity <= 0.0) {
                return GateResult.Error("Item quantity must be greater than 0.")
            }
        }

        // Generate movementId if not provided (e.g. MI-2026-000125)
        val movementId = if (movement.movementId.isBlank()) {
            val count = db.materialMovementDao().countInwardMovements() + 1
            val year = Calendar.getInstance().get(Calendar.YEAR)
            "MI-$year-${String.format("%06d", count)}"
        } else {
            movement.movementId
        }

        val effectiveInTime = if (movement.inTime > 0L) movement.inTime else System.currentTimeMillis()
        val cleanedMovement = movement.copy(
            movementId = movementId,
            movementType = "IN",
            recordedBy = currentUser,
            inTime = effectiveInTime,
            outTime = effectiveInTime,
            created_at = effectiveInTime,
            updated_at = effectiveInTime
        )

        // Save supplier if new
        val existingSupplier = db.supplierDao().getSupplierByName(cleanedMovement.supplierName.trim())
        if (existingSupplier == null) {
            db.supplierDao().insertSupplier(
                Supplier(
                    supplierName = cleanedMovement.supplierName.trim(),
                    status = "Active",
                    isDemo = cleanedMovement.isDemo
                )
            )
        }

        db.materialMovementDao().insertMovement(cleanedMovement)

        // Insert items
        val preparedItems = items.map {
            it.copy(
                movementId = movementId,
                created_at = System.currentTimeMillis(),
                updated_at = System.currentTimeMillis(),
                isDemo = cleanedMovement.isDemo
            )
        }
        db.materialMovementItemDao().insertItems(preparedItems)

        if (docAttachment != null) {
            db.materialDocumentDao().insertDocument(
                docAttachment.copy(relatedId = movementId, isDemo = cleanedMovement.isDemo)
            )
        }

        val itemsSummary = preparedItems.joinToString(", ") { "${it.materialDescription} (${it.quantity} ${it.uom})" }
        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "MATERIAL_IN",
                recordId = movementId,
                newValue = "$movementId | Supplier: ${cleanedMovement.supplierName} | Vehicle: ${cleanedMovement.vehicleNumber}",
                details = "Invoice: ${cleanedMovement.invoiceNumber} | Items: $itemsSummary"
            )
        )

        return GateResult.Success(cleanedMovement)
    }

    // ==========================================
    // PHASE 3: MATERIAL OUTWARD & REPAIR FLOW
    // ==========================================

    suspend fun materialOut(
        movement: MaterialMovement,
        items: List<MaterialMovementItem>,
        repairData: RepairData? = null,
        docAttachment: MaterialDocument? = null,
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<MaterialMovement> {
        if (movement.destinationParty.isBlank() && repairData == null) {
            return GateResult.Error("Destination / Party is mandatory.")
        }
        if (movement.outReason.isBlank()) {
            return GateResult.Error("Outward reason is mandatory.")
        }
        if (movement.authorization.isBlank()) {
            return GateResult.Error("Authorization is mandatory.")
        }
        if (movement.referenceDocument.isBlank()) {
            return GateResult.Error("Reference Document is mandatory.")
        }
        if (items.isEmpty()) {
            return GateResult.Error("At least one material item is required.")
        }
        for (item in items) {
            if (item.materialDescription.isBlank()) {
                return GateResult.Error("Material description cannot be empty.")
            }
            if (item.quantity <= 0.0) {
                return GateResult.Error("Item quantity must be greater than 0.")
            }
        }

        // Generate movementId e.g. MO-2026-000087
        val count = db.materialMovementDao().countOutwardMovements() + 1
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val movementId = "MO-$year-${String.format("%06d", count)}"

        var generatedRepairId: String? = null
        val isRepair = movement.outReason.equals("Repair", ignoreCase = true)
        val effectiveOutTime = if (movement.outTime != null && movement.outTime!! > 0L) movement.outTime!! else System.currentTimeMillis()
        val expectedReturn = if (isRepair) repairData?.expectedReturnDate else movement.expectedReturnDate

        if (expectedReturn != null && expectedReturn <= effectiveOutTime) {
            return GateResult.Error("Expected return date (${DateTimeUtils.formatStandardDate(expectedReturn)}) must be after outward dispatch date & time (${DateTimeUtils.formatStandardDateTime(effectiveOutTime)}).")
        }

        if (isRepair) {
            if (repairData == null || repairData.repairVendor.isBlank()) {
                return GateResult.Error("Repair Vendor is mandatory for repairs.")
            }
            val repCount = db.repairRecordDao().countRepairs() + 1
            generatedRepairId = "REP-$year-${String.format("%06d", repCount)}"

            val primaryItem = items.first()
            val repairRecord = RepairRecord(
                repairId = generatedRepairId,
                movementOutId = movementId,
                materialDescription = primaryItem.materialDescription,
                sentQuantity = primaryItem.quantity,
                uom = primaryItem.uom,
                assetOrSerial = if (primaryItem.serialNumber.isNotBlank()) primaryItem.serialNumber else primaryItem.assetNumber.ifBlank { repairData.assetOrSerial },
                repairVendor = repairData.repairVendor,
                repairReason = repairData.repairReason,
                repairChallanRef = repairData.repairChallanRef.ifBlank { movement.referenceDocument },
                authorizedBy = repairData.authorizedBy.ifBlank { movement.authorization },
                sentDate = effectiveOutTime,
                expectedReturnDate = repairData.expectedReturnDate,
                outTime = effectiveOutTime,
                inTime = null,
                status = "PENDING",
                remarks = repairData.remarks,
                isDemo = movement.isDemo
            )
            db.repairRecordDao().insertRepair(repairRecord)
        }

        val cleanedMovement = movement.copy(
            movementId = movementId,
            movementType = "OUT",
            supplierName = movement.destinationParty, // party
            repairId = generatedRepairId,
            expectedReturnDate = expectedReturn,
            status = if (isRepair) "SENT_FOR_REPAIR" else "COMPLETED",
            recordedBy = currentUser,
            outTime = effectiveOutTime,
            inTime = 0L,
            created_at = effectiveOutTime,
            updated_at = effectiveOutTime
        )

        db.materialMovementDao().insertMovement(cleanedMovement)

        val preparedItems = items.map {
            it.copy(
                movementId = movementId,
                created_at = System.currentTimeMillis(),
                updated_at = System.currentTimeMillis(),
                isDemo = cleanedMovement.isDemo
            )
        }
        db.materialMovementItemDao().insertItems(preparedItems)

        if (docAttachment != null) {
            db.materialDocumentDao().insertDocument(
                docAttachment.copy(relatedId = movementId, isDemo = cleanedMovement.isDemo)
            )
        }

        val itemsSummary = preparedItems.joinToString(", ") { "${it.materialDescription} (${it.quantity} ${it.uom})" }
        val actionType = if (isRepair) "REPAIR_OUT" else "MATERIAL_OUT"
        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = actionType,
                recordId = movementId,
                newValue = "$movementId | Dest: ${cleanedMovement.destinationParty} | Reason: ${cleanedMovement.outReason}${if (generatedRepairId != null) " | Repair ID: $generatedRepairId" else ""}",
                details = "Auth: ${cleanedMovement.authorization} | Items: $itemsSummary"
            )
        )

        return GateResult.Success(cleanedMovement)
    }

    // ==========================================
    // PHASE 3: RETURN FROM REPAIR
    // ==========================================

    suspend fun returnFromRepair(
        repairId: String,
        actualReturnedQty: Double,
        condition: String,
        returnDocRef: String,
        receivedBy: String,
        returnTime: Long = System.currentTimeMillis(),
        remarks: String = "",
        currentUser: String = "Guard (Raju)",
        currentUserRole: String = "GUARD"
    ): GateResult<RepairRecord> {
        val repair = db.repairRecordDao().getRepairById(repairId.trim().uppercase())
            ?: return GateResult.Error("Repair Record #$repairId not found.")

        if (repair.status != "PENDING" && repair.status != "PARTIALLY_RETURNED") {
            return GateResult.Error("Repair #${repair.repairId} has already been completely returned.")
        }

        if (actualReturnedQty <= 0.0) {
            return GateResult.Error("Returned quantity must be greater than 0.")
        }

        val remainingOutside = (repair.sentQuantity - repair.returnedQuantity).coerceAtLeast(0.0)
        if (actualReturnedQty > remainingOutside) {
            return GateResult.Error("Returned quantity ($actualReturnedQty ${repair.uom}) exceeds remaining outside quantity ($remainingOutside ${repair.uom}).")
        }

        if (returnTime < repair.sentDate) {
            return GateResult.Error("Return date & time (${DateTimeUtils.formatStandardDateTime(returnTime)}) cannot be earlier than repair dispatch date & time (${DateTimeUtils.formatStandardDateTime(repair.sentDate)}).")
        }

        val newTotalReturned = repair.returnedQuantity + actualReturnedQty
        val isFullyReturned = newTotalReturned >= repair.sentQuantity
        val newStatus = if (isFullyReturned) "RETURNED_FROM_REPAIR" else "PARTIALLY_RETURNED"

        // Check if returned quantity is partial
        if (!isFullyReturned) {
            val remainingLeft = repair.sentQuantity - newTotalReturned
            db.alertDao().insertAlert(
                Alert(
                    type = "Quantity Mismatch",
                    message = "Repair #${repair.repairId} partial return: $actualReturnedQty ${repair.uom} received. Total returned: $newTotalReturned/${repair.sentQuantity} ${repair.uom}. Still outside: $remainingLeft ${repair.uom}.",
                    severity = "WARNING",
                    relatedId = repair.repairId
                )
            )
        }

        val effectiveReturnTime = returnTime

        // Automatically create a linked Material IN transaction
        val count = db.materialMovementDao().countInwardMovements() + 1
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val linkedInMovementId = "MI-$year-${String.format("%06d", count)}"

        val inMovement = MaterialMovement(
            movementId = linkedInMovementId,
            movementType = "IN",
            supplierName = repair.repairVendor,
            vehicleNumber = "REPAIR_RETURN",
            purpose = "Repair Return",
            invoiceNumber = returnDocRef,
            challanNumber = returnDocRef,
            referenceDocument = repair.repairChallanRef,
            receivedBy = receivedBy,
            recordedBy = currentUser,
            status = "COMPLETED",
            linkedMovementId = repair.movementOutId,
            repairId = repair.repairId,
            remarks = "Returned from repair by ${repair.repairVendor} ($actualReturnedQty ${repair.uom}). Condition: $condition. $remarks",
            inTime = returnTime,
            outTime = returnTime,
            created_at = returnTime,
            updated_at = returnTime,
            isDemo = repair.isDemo
        )
        db.materialMovementDao().insertMovement(inMovement)

        val inItem = MaterialMovementItem(
            movementId = linkedInMovementId,
            materialDescription = repair.materialDescription,
            quantity = actualReturnedQty,
            uom = repair.uom,
            serialNumber = repair.assetOrSerial,
            remarks = "Condition: $condition",
            created_at = returnTime,
            updated_at = returnTime,
            isDemo = repair.isDemo
        )
        db.materialMovementItemDao().insertItem(inItem)

        // Update original OUT movement status
        val outMovement = db.materialMovementDao().getMovementById(repair.movementOutId)
        if (outMovement != null) {
            db.materialMovementDao().updateMovement(
                outMovement.copy(
                    isReturned = isFullyReturned,
                    status = if (isFullyReturned) "RETURNED" else "PARTIALLY_RETURNED",
                    inTime = if (isFullyReturned) returnTime else outMovement.inTime,
                    updated_at = returnTime
                )
            )
        }

        // Update RepairRecord
        val updatedRepair = repair.copy(
            returnedQuantity = newTotalReturned,
            actualReturnDate = if (isFullyReturned) returnTime else (repair.actualReturnDate ?: returnTime),
            inTime = if (isFullyReturned) returnTime else (repair.inTime ?: returnTime),
            conditionOnReturn = condition,
            returnDocumentRef = returnDocRef,
            receivedBy = receivedBy,
            movementInId = linkedInMovementId,
            status = newStatus,
            remarks = remarks,
            updated_at = returnTime
        )
        db.repairRecordDao().updateRepair(updatedRepair)

        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "REPAIR_RETURN",
                recordId = repair.repairId,
                oldValue = "${repair.status} (Returned: ${repair.returnedQuantity}/${repair.sentQuantity} ${repair.uom})",
                newValue = "$newStatus (Now Returned: $newTotalReturned/${repair.sentQuantity} ${repair.uom}) | Linked IN: $linkedInMovementId",
                details = "Vendor: ${repair.repairVendor} | Received Qty: $actualReturnedQty ${repair.uom} | Condition: $condition | Doc: $returnDocRef"
            )
        )

        return GateResult.Success(updatedRepair)
    }

    // ==========================================
    // MATERIAL SEARCH & TRACEABILITY
    // ==========================================

    suspend fun getItemsForMovement(movementId: String) =
        db.materialMovementItemDao().getItemsForMovement(movementId)

    suspend fun searchMaterialTraceability(query: String): List<Pair<MaterialMovement, List<MaterialMovementItem>>> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val matchingMovements = db.materialMovementDao().searchByReference(q)
        val matchingItems = db.materialMovementItemDao().searchItems(q)

        val movementIds = (matchingMovements.map { it.movementId } + matchingItems.map { it.movementId }).distinct()

        return movementIds.mapNotNull { mId ->
            val movement = db.materialMovementDao().getMovementById(mId)
            if (movement != null) {
                val items = db.materialMovementItemDao().getItemsForMovement(mId)
                Pair(movement, items)
            } else null
        }
    }

    suspend fun searchRepairs(query: String) =
        db.repairRecordDao().searchRepairs(query.trim())

    suspend fun getVisitorByPassId(passId: String): VisitorEntry? {
        val clean = passId.trim().uppercase()
        return db.visitorEntryDao().getEntryByPassId(clean)
    }

    suspend fun getMovementWithItems(movementId: String): Pair<MaterialMovement, List<MaterialMovementItem>>? {
        val m = db.materialMovementDao().getMovementById(movementId.trim().uppercase()) ?: return null
        val items = db.materialMovementItemDao().getItemsForMovement(m.movementId)
        return Pair(m, items)
    }

    suspend fun getRepairById(repairId: String): RepairRecord? {
        return db.repairRecordDao().getRepairById(repairId.trim().uppercase())
    }

    suspend fun getVehicleEntryByIdOrNumber(query: String): VehicleEntry? {
        val clean = query.trim().uppercase()
        return db.vehicleEntryDao().getActiveEntryForVehicle(clean)
            ?: db.vehicleEntryDao().getEntryById(clean)
    }

    suspend fun checkAndTriggerOverdueAlerts() {
        val now = System.currentTimeMillis()
        // Check pending repairs
        val repairs = db.repairRecordDao().searchRepairs("")
        for (r in repairs) {
            val remaining = (r.sentQuantity - r.returnedQuantity).coerceAtLeast(0.0)
            if (r.status in listOf("PENDING", "PARTIALLY_RETURNED") && r.expectedReturnDate < now && remaining > 0.0) {
                val alert = Alert(
                    type = "Repair Overdue",
                    message = "Repair #${r.repairId} for ${r.materialDescription} ($remaining/${r.sentQuantity} ${r.uom} with ${r.repairVendor}) is OVERDUE! Expected: ${formatShortDate(r.expectedReturnDate)}.",
                    severity = "WARNING",
                    relatedId = r.repairId
                )
                db.alertDao().insertAlert(alert)
            }
        }
    }

    // Masters
    suspend fun addSupplier(supplier: Supplier) = db.supplierDao().insertSupplier(supplier)
    suspend fun addMaterialMaster(material: MaterialMaster) = db.materialMasterDao().insertMaterial(material)

    // Common
    suspend fun dismissAlert(
        alert: Alert,
        currentUser: String = "Supervisor",
        currentUserRole: String = "SUPERVISOR"
    ) {
        db.alertDao().updateAlert(alert.copy(isDismissed = true))
        db.auditLogDao().insertLog(
            AuditLog(
                user = currentUser,
                userRole = currentUserRole,
                action = "ALERT_DISMISS",
                recordId = alert.id,
                details = "Dismissed alert: ${alert.type} - ${alert.message}"
            )
        )
    }

    suspend fun searchVehicles(query: String) = db.vehicleEntryDao().searchEntries(query.trim())
    suspend fun searchVisitors(query: String) = db.visitorEntryDao().searchEntries(query.trim())

    suspend fun getVehicleEntriesInRange(start: Long, end: Long) =
        db.vehicleEntryDao().getEntriesInRange(start, end)

    suspend fun getVisitorEntriesInRange(start: Long, end: Long) =
        db.visitorEntryDao().getEntriesInRange(start, end)

    suspend fun getMaterialMovementsInRange(start: Long, end: Long) =
        db.materialMovementDao().getMovementsInRange(start, end)

    suspend fun seedDemoData() {
        val now = System.currentTimeMillis()
        val hour = 3600 * 1000L
        val day = 24 * hour

        // 1. Demo Vehicles
        val demoVehicles = listOf(
            VehicleEntry(
                vehicleNumber = "DL01AB1234",
                vehicleType = "Truck",
                driverName = "Ramesh Kumar",
                driverMobile = "9876543210",
                transporter = "Swift Logistics",
                purpose = "Delivery",
                openingKm = 82450,
                inTime = now - (5 * hour + 15 * 60 * 1000),
                status = "Inside",
                isDemo = true
            ),
            VehicleEntry(
                vehicleNumber = "HR38AB5678",
                vehicleType = "Container",
                driverName = "Suresh Patel",
                driverMobile = "9871234567",
                transporter = "Blue Dart Intermodal",
                purpose = "Pickup",
                openingKm = 41200,
                inTime = now - (2 * hour),
                status = "Inside",
                isDemo = true
            ),
            VehicleEntry(
                vehicleNumber = "MH04CD9087",
                vehicleType = "Tempo",
                driverName = "Manoj Yadav",
                driverMobile = "9899123456",
                transporter = "Local Express",
                purpose = "Material Movement",
                openingKm = 15300,
                closingKm = 15348,
                inTime = now - (4 * hour),
                outTime = now - (1 * hour),
                status = "Completed",
                isDemo = true
            )
        )

        // 2. Demo Visitors
        val demoVisitors = listOf(
            VisitorEntry(
                visitorName = "Priya Sharma",
                mobileNumber = "9811223344",
                company = "Deloitte Consulting",
                host = "Accounts Dept (Mr. Verma)",
                purpose = "Financial Audit",
                passId = "PASS101",
                inTime = now - (3 * hour + 45 * 60 * 1000),
                status = "Inside",
                isDemo = true
            ),
            VisitorEntry(
                visitorName = "Anil Verma",
                mobileNumber = "9822334455",
                company = "Apex Systems",
                host = "IT Operations",
                purpose = "Server Maintenance",
                passId = "PASS102",
                inTime = now - (1 * hour),
                status = "Inside",
                isDemo = true
            )
        )

        // 3. Demo Suppliers & Materials Master
        val demoSuppliers = listOf(
            Supplier(supplierName = "Tata Steel Processing", contactPerson = "R. K. Gupta", mobile = "9810011223", status = "Active", isDemo = true),
            Supplier(supplierName = "XYZ Engineering Works", contactPerson = "Vikram Shah", mobile = "9820033445", status = "Active", isDemo = true),
            Supplier(supplierName = "Apex Electricals & Hardware", contactPerson = "Amit Sinha", mobile = "9830055667", status = "Active", isDemo = true)
        )

        val demoMaterials = listOf(
            MaterialMaster(materialName = "Coffee Machine", itemCode = "CM-00125", category = "Equipment", uom = "NOS", isAsset = true, isDemo = true),
            MaterialMaster(materialName = "Steel Sheets 2mm", itemCode = "SS-200", category = "Raw Material", uom = "KG", isDemo = true),
            MaterialMaster(materialName = "5HP Induction Motor", itemCode = "MOT-5HP", category = "Equipment", uom = "NOS", isAsset = true, isDemo = true),
            MaterialMaster(materialName = "Corrugated Packaging Box", itemCode = "BOX-L3", category = "Packaging", uom = "BOX", isDemo = true)
        )

        // 4. Demo Material Movements & Repairs
        val mi1 = MaterialMovement(
            movementId = "MI-2026-000101",
            movementType = "IN",
            supplierName = "Tata Steel Processing",
            vehicleNumber = "DL01AB1234",
            driverName = "Ramesh Kumar",
            invoiceNumber = "INV-4587",
            challanNumber = "CH-9021",
            poNumber = "PO-8812",
            purpose = "Production Raw Material",
            gate = "Main Gate",
            status = "COMPLETED",
            inTime = now - (3 * hour),
            outTime = now - (3 * hour),
            created_at = now - (3 * hour),
            isDemo = true
        )
        val mi1Items = listOf(
            MaterialMovementItem(
                movementId = "MI-2026-000101",
                materialDescription = "Steel Sheets 2mm",
                itemCode = "SS-200",
                quantity = 500.0,
                uom = "KG",
                isDemo = true
            ),
            MaterialMovementItem(
                movementId = "MI-2026-000101",
                materialDescription = "Angle Brackets 50x50",
                itemCode = "AB-5050",
                quantity = 150.0,
                uom = "NOS",
                isDemo = true
            )
        )

        // Material OUT for Repair (Under Repair / Overdue)
        val mo1 = MaterialMovement(
            movementId = "MO-2026-000087",
            movementType = "OUT",
            supplierName = "XYZ Engineering Works",
            destinationParty = "XYZ Engineering Works",
            vehicleNumber = "MH04CD9087",
            driverName = "Manoj Yadav",
            referenceDocument = "RGP-4401",
            outReason = "Repair",
            authorization = "Plant Manager (Mr. Saxena)",
            repairId = "REP-2026-000021",
            expectedReturnDate = now - (1 * day), // OVERDUE by 1 day
            isReturned = false,
            status = "SENT_FOR_REPAIR",
            outTime = now - (6 * day),
            inTime = 0L,
            created_at = now - (6 * day),
            isDemo = true
        )
        val mo1Items = listOf(
            MaterialMovementItem(
                movementId = "MO-2026-000087",
                materialDescription = "Coffee Machine",
                itemCode = "CM-00125",
                quantity = 1.0,
                uom = "NOS",
                assetNumber = "CM-00125",
                serialNumber = "SN-998822",
                remarks = "Heating element burnt",
                isDemo = true
            )
        )
        val rep1 = RepairRecord(
            repairId = "REP-2026-000021",
            movementOutId = "MO-2026-000087",
            materialDescription = "Coffee Machine CM-00125",
            sentQuantity = 1.0,
            uom = "NOS",
            assetOrSerial = "CM-00125",
            repairVendor = "XYZ Engineering Works",
            repairReason = "Heating element replacement",
            repairChallanRef = "RGP-4401",
            authorizedBy = "Mr. Saxena",
            sentDate = now - (6 * day),
            expectedReturnDate = now - (1 * day), // OVERDUE
            outTime = now - (6 * day),
            inTime = null,
            status = "PENDING",
            isDemo = true
        )

        // 5. Demo Alerts
        val demoAlerts = listOf(
            Alert(
                type = "Long Vehicle Stay",
                message = "Vehicle DL01AB1234 has been inside for 5h 15m.",
                severity = "WARNING",
                timestamp = now - (15 * 60 * 1000),
                isDemo = true
            ),
            Alert(
                type = "Repair Overdue",
                message = "Repair REP-2026-000021 (Coffee Machine with XYZ Engineering) is OVERDUE! Expected return was yesterday.",
                severity = "WARNING",
                relatedId = "REP-2026-000021",
                timestamp = now - (2 * hour),
                isDemo = true
            )
        )

        demoVehicles.forEach { db.vehicleEntryDao().insertEntry(it) }
        demoVisitors.forEach { db.visitorEntryDao().insertEntry(it) }
        demoSuppliers.forEach { db.supplierDao().insertSupplier(it) }
        demoMaterials.forEach { db.materialMasterDao().insertMaterial(it) }
        db.materialMovementDao().insertMovement(mi1)
        db.materialMovementItemDao().insertItems(mi1Items)
        db.materialMovementDao().insertMovement(mo1)
        db.materialMovementItemDao().insertItems(mo1Items)
        db.repairRecordDao().insertRepair(rep1)
        demoAlerts.forEach { db.alertDao().insertAlert(it) }
    }

    suspend fun clearDemoData() {
        db.vehicleEntryDao().clearDemoEntries()
        db.visitorEntryDao().clearDemoEntries()
        db.vehicleDao().clearDemoVehicles()
        db.visitorDao().clearDemoVisitors()
        db.alertDao().clearDemoAlerts()
        db.supplierDao().clearDemoSuppliers()
        db.materialMasterDao().clearDemoMaterials()
        db.materialMovementDao().clearDemoMovements()
        db.materialMovementItemDao().clearDemoItems()
        db.repairRecordDao().clearDemoRepairs()
        db.materialDocumentDao().clearDemoDocs()
    }

    private fun formatShortTime(time: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(time))
    }

    private fun formatShortDate(time: Long): String {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        return sdf.format(Date(time))
    }

    // ==========================================
    // SYNC FLOWS
    // ==========================================

    suspend fun getPendingVehicleEntries() = db.vehicleEntryDao().getPendingSyncEntries()
    suspend fun updateVehicleSyncStatus(id: String, status: String) = db.vehicleEntryDao().updateSyncStatus(id, status)

    suspend fun getPendingVisitorEntries() = db.visitorEntryDao().getPendingSyncEntries()
    suspend fun updateVisitorSyncStatus(id: String, status: String) = db.visitorEntryDao().updateSyncStatus(id, status)

    suspend fun getPendingMaterialMovements() = db.materialMovementDao().getPendingSyncMovements()
    suspend fun updateMaterialSyncStatus(id: String, status: String) = db.materialMovementDao().updateSyncStatus(id, status)
}
