package com.example.util.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BulkSyncPayload(
    val vehicles: List<VehicleEntryDto> = emptyList(),
    val visitors: List<VisitorEntryDto> = emptyList(),
    val movements: List<MaterialMovementDto> = emptyList(),
    @SerialName("movement_items")
    val movementItems: List<MaterialMovementItemDto> = emptyList()
)

@Serializable
data class VehicleEntryDto(
    val entryId: String,
    val siteId: String? = null,
    val vehicleNumber: String,
    val vehicleType: String,
    val driverName: String,
    val driverMobile: String,
    val transporter: String,
    val purpose: String,
    val openingKm: Int,
    val closingKm: Int?,
    val poNumber: String,
    val invoiceNumber: String,
    val challanNumber: String,
    val ewayBill: String,
    val dock: String,
    val remarks: String,
    val inTime: Long,
    val outTime: Long?,
    val gate: String,
    val recordedBy: String,
    val status: String,
    val supervisorOverride: Boolean,
    val supervisorNotes: String,
    val isDemo: Boolean
)

@Serializable
data class VisitorEntryDto(
    val entryId: String,
    val siteId: String? = null,
    val visitorName: String,
    val mobileNumber: String,
    val company: String,
    val host: String,
    val purpose: String,
    val vehicleNumber: String,
    val inTime: Long,
    val outTime: Long?,
    val gate: String,
    val recordedBy: String,
    val status: String,
    val passId: String,
    val isDemo: Boolean
)

@Serializable
data class MaterialMovementDto(
    val movementId: String,
    val siteId: String? = null,
    val movementType: String,
    val supplierName: String,
    val destinationParty: String,
    val vehicleNumber: String,
    val invoiceNumber: String,
    val challanNumber: String,
    val poNumber: String,
    val ewayBill: String,
    val purpose: String,
    val outReason: String,
    val repairId: String?,
    val driverName: String,
    val driverMobile: String,
    val transporter: String,
    val receivedBy: String,
    val authorizedBy: String,
    val remarks: String,
    val inTime: Long,
    val outTime: Long?,
    val expectedReturnDate: Long?,
    val actualReturnDate: Long?,
    val gate: String,
    val recordedBy: String,
    val status: String,
    val isDemo: Boolean
)

@Serializable
data class MaterialMovementItemDto(
    val itemId: String,
    val siteId: String? = null,
    val movementId: String,
    val materialDescription: String,
    val itemCode: String,
    val quantity: Double,
    val uom: String,
    val serialNumber: String,
    val assetNumber: String,
    val remarks: String
)

@Serializable
data class SyncResponseDto(
    val success: Boolean,
    @SerialName("vehicles_synced") val vehiclesSynced: Int? = 0,
    @SerialName("visitors_synced") val visitorsSynced: Int? = 0,
    @SerialName("movements_synced") val movementsSynced: Int? = 0,
    @SerialName("items_synced") val itemsSynced: Int? = 0,
    val error: String? = null,
    val timestamp: String? = null
)
