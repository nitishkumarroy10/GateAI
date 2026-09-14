package com.example.util.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.local.GateDatabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

enum class SyncState {
    IDLE, SYNCING, ERROR, OFFLINE
}

data class SyncInfo(
    val state: SyncState = SyncState.IDLE,
    val pendingCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val errorMessage: String? = null
)

class SyncManager private constructor(
    private val context: Context,
    private val database: GateDatabase
) {
    private val _syncInfo = MutableStateFlow(SyncInfo())
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    private val supabase = SupabaseClientProvider.client
    
    // JSON setup for decoding the response
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pushPendingData() = withContext(Dispatchers.IO) {
        _syncInfo.value = _syncInfo.value.copy(state = SyncState.SYNCING, errorMessage = null)
        try {
            val pendingVehicles = database.vehicleEntryDao().getEntriesBySyncStatus("PENDING")
            val pendingVisitors = database.visitorEntryDao().getEntriesBySyncStatus("PENDING")
            val pendingMovements = database.materialMovementDao().getMovementsBySyncStatus("PENDING")
            // Fetch movement items for pending movements
            val pendingMovementIds = pendingMovements.map { it.movementId }
            val pendingItems = if (pendingMovementIds.isNotEmpty()) {
                database.materialMovementDao().getItemsByMovementIds(pendingMovementIds)
            } else {
                emptyList()
            }

            val totalPending = pendingVehicles.size + pendingVisitors.size + pendingMovements.size
            _syncInfo.value = _syncInfo.value.copy(pendingCount = totalPending)

            if (totalPending == 0) {
                _syncInfo.value = _syncInfo.value.copy(state = SyncState.IDLE, lastSyncTime = System.currentTimeMillis())
                return@withContext
            }

            // Map entities to DTOs
            val vehicleDtos = pendingVehicles.map { v ->
                VehicleEntryDto(
                    entryId = v.entryId,
                    vehicleNumber = v.vehicleNumber,
                    vehicleType = v.vehicleType,
                    driverName = v.driverName,
                    driverMobile = v.driverMobile,
                    transporter = v.transporter,
                    purpose = v.purpose,
                    openingKm = v.openingKm,
                    closingKm = v.closingKm,
                    poNumber = v.poNumber,
                    invoiceNumber = v.invoiceNumber,
                    challanNumber = v.challanNumber,
                    ewayBill = v.ewayBill,
                    dock = v.dock,
                    remarks = v.remarks,
                    inTime = v.inTime,
                    outTime = v.outTime,
                    gate = v.gate,
                    recordedBy = v.recordedBy,
                    status = v.status,
                    supervisorOverride = v.supervisorOverride,
                    supervisorNotes = v.supervisorNotes,
                    isDemo = v.isDemo
                )
            }

            val visitorDtos = pendingVisitors.map { v ->
                VisitorEntryDto(
                    entryId = v.entryId,
                    visitorName = v.visitorName,
                    mobileNumber = v.mobileNumber,
                    company = v.company,
                    host = v.host,
                    purpose = v.purpose,
                    vehicleNumber = v.vehicleNumber,
                    inTime = v.inTime,
                    outTime = v.outTime,
                    gate = v.gate,
                    recordedBy = v.recordedBy,
                    status = v.status,
                    passId = v.passId,
                    isDemo = v.isDemo
                )
            }

            val movementDtos = pendingMovements.map { m ->
                MaterialMovementDto(
                    movementId = m.movementId,
                    movementType = m.movementType,
                    supplierName = m.supplierName,
                    destinationParty = m.destinationParty,
                    vehicleNumber = m.vehicleNumber,
                    invoiceNumber = m.invoiceNumber,
                    challanNumber = m.challanNumber,
                    poNumber = m.poNumber,
                    ewayBill = m.ewayBill,
                    purpose = m.purpose,
                    outReason = m.outReason,
                    repairId = m.repairId,
                    driverName = m.driverName,
                    driverMobile = m.driverMobile,
                    transporter = m.transporter,
                    receivedBy = m.receivedBy,
                    authorizedBy = m.authorizedBy,
                    remarks = m.remarks,
                    inTime = m.inTime,
                    outTime = m.outTime,
                    expectedReturnDate = m.expectedReturnDate,
                    actualReturnDate = m.actualReturnDate,
                    gate = m.gate,
                    recordedBy = m.recordedBy,
                    status = m.status,
                    isDemo = m.isDemo
                )
            }

            val itemDtos = pendingItems.map { i ->
                MaterialMovementItemDto(
                    itemId = i.itemId,
                    movementId = i.movementId,
                    materialDescription = i.materialDescription,
                    itemCode = i.itemCode,
                    quantity = i.quantity,
                    uom = i.uom,
                    serialNumber = i.serialNumber,
                    assetNumber = i.assetNumber,
                    remarks = i.remarks
                )
            }

            val payload = BulkSyncPayload(
                vehicles = vehicleDtos,
                visitors = visitorDtos,
                movements = movementDtos,
                movementItems = itemDtos
            )

            // Call Supabase RPC
            val responseString = supabase.postgrest.rpc(
                function = "bulk_sync_gate_data",
                parameters = payload
            ).data

            val response = json.decodeFromString<SyncResponseDto>(responseString)

            if (response.success) {
                // Update local Room database syncStatus
                database.runInTransaction {
                    if (pendingVehicles.isNotEmpty()) {
                        val ids = pendingVehicles.map { it.entryId }
                        database.vehicleEntryDao().updateSyncStatusBlocking(ids, "SYNCED")
                    }
                    if (pendingVisitors.isNotEmpty()) {
                        val ids = pendingVisitors.map { it.entryId }
                        database.visitorEntryDao().updateSyncStatusBlocking(ids, "SYNCED")
                    }
                    if (pendingMovements.isNotEmpty()) {
                        val ids = pendingMovements.map { it.movementId }
                        database.materialMovementDao().updateSyncStatusBlocking(ids, "SYNCED")
                    }
                }
                
                _syncInfo.value = _syncInfo.value.copy(
                    state = SyncState.IDLE,
                    pendingCount = 0,
                    lastSyncTime = System.currentTimeMillis()
                )
                Log.d("SyncManager", "Sync successful: ${response.vehiclesSynced} vehicles, ${response.visitorsSynced} visitors, ${response.movementsSynced} movements.")
            } else {
                Log.e("SyncManager", "Sync RPC returned error: ${response.error}")
                _syncInfo.value = _syncInfo.value.copy(
                    state = SyncState.ERROR,
                    errorMessage = response.error ?: "Unknown RPC error"
                )
            }

        } catch (e: Exception) {
            Log.e("SyncManager", "Sync exception: ${e.message}", e)
            _syncInfo.value = _syncInfo.value.copy(
                state = SyncState.ERROR,
                errorMessage = e.message
            )
        }
    }

    // A helper method for initiating periodic background sync
    fun enqueuePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "GateDataSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context, database: GateDatabase): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext, database).also { INSTANCE = it }
            }
        }
    }
}

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = GateDatabase.getDatabase(applicationContext)
        val syncManager = SyncManager.getInstance(applicationContext, database)
        
        syncManager.pushPendingData()
        
        // If state is not ERROR, consider it success or idle
        return if (syncManager.syncInfo.value.state != SyncState.ERROR) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
