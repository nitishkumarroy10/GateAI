package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE vehicleNumber = :number")
    suspend fun getVehicle(number: String): Vehicle?

    @Query("SELECT * FROM vehicles ORDER BY vehicleNumber ASC")
    fun getAllVehicles(): Flow<List<Vehicle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE isDemo = 1")
    suspend fun clearDemoVehicles()
}

@Dao
interface VisitorDao {
    @Query("SELECT * FROM visitors ORDER BY visitorName ASC")
    fun getAllVisitors(): Flow<List<Visitor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitor(visitor: Visitor)

    @Query("DELETE FROM visitors WHERE isDemo = 1")
    suspend fun clearDemoVisitors()
}

@Dao
interface VehicleEntryDao {
    @Query("SELECT * FROM vehicle_entries WHERE syncStatus = :status")
    suspend fun getEntriesBySyncStatus(status: String = "PENDING"): List<VehicleEntry>

    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(ids: List<String>, status: String)
    @Query("SELECT * FROM vehicle_entries ORDER BY inTime DESC")
    fun getAllEntries(): Flow<List<VehicleEntry>>

    @Query("SELECT * FROM vehicle_entries WHERE status = 'Inside' ORDER BY inTime ASC")
    fun getInsideVehicles(): Flow<List<VehicleEntry>>

    @Query("SELECT * FROM vehicle_entries WHERE entryId = :id")
    suspend fun getEntryById(id: String): VehicleEntry?

    @Query("SELECT * FROM vehicle_entries WHERE vehicleNumber = :number AND status = 'Inside' LIMIT 1")
    suspend fun getActiveEntryForVehicle(number: String): VehicleEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VehicleEntry)

    @Update
    suspend fun updateEntry(entry: VehicleEntry)

    @Query("SELECT * FROM vehicle_entries WHERE vehicleNumber LIKE '%' || :query || '%' OR driverName LIKE '%' || :query || '%' OR entryId LIKE '%' || :query || '%' OR transporter LIKE '%' || :query || '%' ORDER BY inTime DESC")
    suspend fun searchEntries(query: String): List<VehicleEntry>

    @Query("SELECT * FROM vehicle_entries WHERE inTime >= :startTime AND inTime <= :endTime ORDER BY inTime DESC")
    suspend fun getEntriesInRange(startTime: Long, endTime: Long): List<VehicleEntry>

    @Query("SELECT * FROM vehicle_entries WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncEntries(): List<VehicleEntry>

    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId = :id")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM vehicle_entries WHERE isDemo = 1")
    suspend fun clearDemoEntries()
}

@Dao
interface VisitorEntryDao {
    @Query("SELECT * FROM visitor_entries WHERE syncStatus = :status")
    suspend fun getEntriesBySyncStatus(status: String = "PENDING"): List<VisitorEntry>

    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(ids: List<String>, status: String)
    @Query("SELECT * FROM visitor_entries ORDER BY inTime DESC")
    fun getAllEntries(): Flow<List<VisitorEntry>>

    @Query("SELECT * FROM visitor_entries WHERE status = 'Inside' ORDER BY inTime ASC")
    fun getInsideVisitors(): Flow<List<VisitorEntry>>

    @Query("SELECT * FROM visitor_entries WHERE passId = :passId OR entryId = :passId LIMIT 1")
    suspend fun getEntryByPassId(passId: String): VisitorEntry?

    @Query("SELECT * FROM visitor_entries WHERE mobileNumber = :mobile AND status = 'Inside' LIMIT 1")
    suspend fun getActiveEntryByMobile(mobile: String): VisitorEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VisitorEntry)

    @Update
    suspend fun updateEntry(entry: VisitorEntry)

    @Query("SELECT * FROM visitor_entries WHERE visitorName LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%' OR passId LIKE '%' || :query || '%' OR host LIKE '%' || :query || '%' OR mobileNumber LIKE '%' || :query || '%' ORDER BY inTime DESC")
    suspend fun searchEntries(query: String): List<VisitorEntry>

    @Query("SELECT * FROM visitor_entries WHERE inTime >= :startTime AND inTime <= :endTime ORDER BY inTime DESC")
    suspend fun getEntriesInRange(startTime: Long, endTime: Long): List<VisitorEntry>

    @Query("SELECT * FROM visitor_entries WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncEntries(): List<VisitorEntry>

    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId = :id")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM visitor_entries WHERE isDemo = 1")
    suspend fun clearDemoEntries()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE isDismissed = 0 ORDER BY timestamp DESC")
    fun getActiveAlerts(): Flow<List<Alert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert)

    @Update
    suspend fun updateAlert(alert: Alert)

    @Query("DELETE FROM alerts WHERE isDemo = 1")
    suspend fun clearDemoAlerts()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 300")
    fun getRecentAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

// ==========================================
// PHASE 3: MATERIAL, SUPPLIER & REPAIR DAOS
// ==========================================

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY supplierName ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE supplierName = :name LIMIT 1")
    suspend fun getSupplierByName(name: String): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier)

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("DELETE FROM suppliers WHERE isDemo = 1")
    suspend fun clearDemoSuppliers()
}

@Dao
interface MaterialMasterDao {
    @Query("SELECT * FROM materials_master ORDER BY materialName ASC")
    fun getAllMaterials(): Flow<List<MaterialMaster>>

    @Query("SELECT * FROM materials_master WHERE materialName LIKE '%' || :query || '%' OR itemCode LIKE '%' || :query || '%'")
    suspend fun searchMaterials(query: String): List<MaterialMaster>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialMaster)

    @Query("DELETE FROM materials_master WHERE isDemo = 1")
    suspend fun clearDemoMaterials()
}

@Dao
interface MaterialMovementDao {
    @Query("SELECT * FROM material_movement_items WHERE movementId IN (:movementIds)")
    suspend fun getItemsByMovementIds(movementIds: List<String>): List<MaterialMovementItem>
    @Query("SELECT * FROM material_movements WHERE syncStatus = :status")
    suspend fun getMovementsBySyncStatus(status: String = "PENDING"): List<MaterialMovement>

    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(ids: List<String>, status: String)
    @Query("SELECT * FROM material_movements ORDER BY created_at DESC")
    fun getAllMovements(): Flow<List<MaterialMovement>>

    @Query("SELECT * FROM material_movements WHERE movementType = :type ORDER BY created_at DESC")
    fun getMovementsByType(type: String): Flow<List<MaterialMovement>>

    @Query("SELECT * FROM material_movements WHERE movementId = :movementId LIMIT 1")
    suspend fun getMovementById(movementId: String): MaterialMovement?

    @Query("SELECT * FROM material_movements WHERE movementType = 'OUT' AND expectedReturnDate IS NOT NULL AND isReturned = 0 ORDER BY expectedReturnDate ASC")
    fun getCurrentlyOutside(): Flow<List<MaterialMovement>>

    @Query("SELECT COUNT(*) FROM material_movements WHERE movementType = 'IN'")
    suspend fun countInwardMovements(): Int

    @Query("SELECT COUNT(*) FROM material_movements WHERE movementType = 'OUT'")
    suspend fun countOutwardMovements(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: MaterialMovement)

    @Update
    suspend fun updateMovement(movement: MaterialMovement)

    @Query("SELECT * FROM material_movements WHERE invoiceNumber LIKE '%' || :query || '%' OR challanNumber LIKE '%' || :query || '%' OR poNumber LIKE '%' || :query || '%' OR ewayBill LIKE '%' || :query || '%' OR movementId LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun searchByReference(query: String): List<MaterialMovement>

    @Query("SELECT * FROM material_movements WHERE created_at >= :startTime AND created_at <= :endTime ORDER BY created_at DESC")
    suspend fun getMovementsInRange(startTime: Long, endTime: Long): List<MaterialMovement>

    @Query("SELECT * FROM material_movements WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncMovements(): List<MaterialMovement>

    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId = :id")
    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")
    fun updateSyncStatusBlocking(ids: List<String>, status: String)
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM material_movements WHERE isDemo = 1")
    suspend fun clearDemoMovements()
}

@Dao
interface MaterialMovementItemDao {
    @Query("SELECT * FROM material_movement_items WHERE movementId = :movementId ORDER BY created_at ASC")
    suspend fun getItemsForMovement(movementId: String): List<MaterialMovementItem>

    @Query("SELECT * FROM material_movement_items WHERE movementId = :movementId ORDER BY created_at ASC")
    fun getItemsFlowForMovement(movementId: String): Flow<List<MaterialMovementItem>>

    @Query("SELECT * FROM material_movement_items WHERE serialNumber LIKE '%' || :query || '%' OR assetNumber LIKE '%' || :query || '%' OR materialDescription LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun searchItems(query: String): List<MaterialMovementItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MaterialMovementItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MaterialMovementItem>)

    @Query("DELETE FROM material_movement_items WHERE isDemo = 1")
    suspend fun clearDemoItems()
}

@Dao
interface RepairRecordDao {
    @Query("SELECT * FROM repair_records ORDER BY sentDate DESC")
    fun getAllRepairs(): Flow<List<RepairRecord>>

    @Query("SELECT * FROM repair_records WHERE status IN ('PENDING', 'PARTIALLY_RETURNED') ORDER BY expectedReturnDate ASC")
    fun getPendingRepairs(): Flow<List<RepairRecord>>

    @Query("SELECT * FROM repair_records WHERE repairId = :repairId LIMIT 1")
    suspend fun getRepairById(repairId: String): RepairRecord?

    @Query("SELECT COUNT(*) FROM repair_records")
    suspend fun countRepairs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepair(repair: RepairRecord)

    @Update
    suspend fun updateRepair(repair: RepairRecord)

    @Query("SELECT * FROM repair_records WHERE repairId LIKE '%' || :query || '%' OR repairVendor LIKE '%' || :query || '%' OR assetOrSerial LIKE '%' || :query || '%' OR materialDescription LIKE '%' || :query || '%' ORDER BY sentDate DESC")
    suspend fun searchRepairs(query: String): List<RepairRecord>

    @Query("DELETE FROM repair_records WHERE isDemo = 1")
    suspend fun clearDemoRepairs()
}

@Dao
interface MaterialDocumentDao {
    @Query("SELECT * FROM material_documents WHERE relatedId = :relatedId ORDER BY created_at DESC")
    suspend fun getDocumentsForRecord(relatedId: String): List<MaterialDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: MaterialDocument)

    @Query("DELETE FROM material_documents WHERE isDemo = 1")
    suspend fun clearDemoDocs()
}
