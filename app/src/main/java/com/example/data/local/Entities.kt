package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val vehicleNumber: String,
    val vehicleType: String,
    val driverName: String,
    val driverMobile: String = "",
    val transporter: String = "",
    val status: String = "Active"
    val isDemo: Boolean = false
)

@Entity(tableName = "visitors")
data class Visitor(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val visitorName: String,
    val mobileNumber: String,
    val company: String = "",
    val isDemo: Boolean = false
)

@Entity(tableName = "vehicle_entries")
data class VehicleEntry(
    @PrimaryKey val entryId: String = UUID.randomUUID().toString(),
    val vehicleNumber: String, // Normalized, e.g. "DL01AB1234"
    val vehicleType: String,
    val driverName: String,
    val driverMobile: String = "",
    val transporter: String = "",
    val purpose: String,
    val openingKm: Int,
    val closingKm: Int? = null,
    val poNumber: String = "",
    val invoiceNumber: String = "",
    val challanNumber: String = "",
    val ewayBill: String = "",
    val dock: String = "",
    val remarks: String = "",
    val inTime: Long = System.currentTimeMillis(),
    val outTime: Long? = null,
    val gate: String = "Main Gate",
    val recordedBy: String = "Guard (Raju)",
    val status: String = "Inside", // "Inside", "Completed"
    val isDemo: Boolean = false,
    val syncStatus: String = "PENDING", // "SYNCED", "PENDING", "FAILED"
    val supervisorOverride: Boolean = false,
    val supervisorNotes: String = ""
) {
    val entryDate: String get() = com.example.util.DateTimeUtils.formatStandardDate(inTime)
    val entryTime: String get() = com.example.util.DateTimeUtils.formatStandardTime(inTime)
    val exitDate: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardDate(it) }
    val exitTime: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardTime(it) }
}

@Entity(tableName = "visitor_entries")
data class VisitorEntry(
    @PrimaryKey val entryId: String = UUID.randomUUID().toString(),
    val visitorName: String,
    val mobileNumber: String,
    val company: String = "",
    val host: String,
    val purpose: String,
    val vehicleNumber: String = "",
    val inTime: Long = System.currentTimeMillis(),
    val outTime: Long? = null,
    val gate: String = "Main Gate",
    val recordedBy: String = "Guard (Raju)",
    val status: String = "Inside", // "Inside", "Completed"
    val passId: String = UUID.randomUUID().toString().substring(0, 8).uppercase(),
    val isDemo: Boolean = false,
    val syncStatus: String = "PENDING"
) {
    val entryDate: String get() = com.example.util.DateTimeUtils.formatStandardDate(inTime)
    val entryTime: String get() = com.example.util.DateTimeUtils.formatStandardTime(inTime)
    val exitDate: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardDate(it) }
    val exitTime: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardTime(it) }
}

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // "Long Stay", "Duplicate Entry", "KM Error", "Unusual KM", "Missing Exit", "Repair Overdue", "Expected Return Overdue", "Quantity Mismatch", "Missing Authorization"
    val message: String,
    val severity: String = "WARNING", // "WARNING", "CRITICAL", "INFO"
    val relatedId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
    val isDemo: Boolean = false
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val user: String,
    val userRole: String,
    val action: String, // "VEHICLE_IN", "VEHICLE_OUT", "VISITOR_IN", "VISITOR_OUT", "MATERIAL_IN", "MATERIAL_OUT", "REPAIR_OUT", "REPAIR_RETURN", "SUPERVISOR_OVERRIDE", "RECORD_EDIT", "ALERT_DISMISS", "MATERIAL_APPROVE", "MATERIAL_REJECT"
    val recordId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val oldValue: String? = null,
    val newValue: String? = null,
    val details: String = ""
)

// ==========================================
// PHASE 3: MATERIAL MOVEMENT & REPAIR ENTITIES
// ==========================================

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val supplierName: String,
    val contactPerson: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val gstin: String = "",
    val status: String = "Active"
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(tableName = "materials_master")
data class MaterialMaster(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val materialName: String,
    val itemCode: String = "",
    val category: String = "Raw Material", // "Raw Material", "Equipment", "Tools", "Packaging", "Spare Parts", "IT Hardware"
    val uom: String = "NOS", // "NOS", "KG", "BOX", "MTR", "LTR", "SET"
    val isSerialised: Boolean = false,
    val isAsset: Boolean = false,
    val status: String = "Active",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(tableName = "material_movements")
data class MaterialMovement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val movementId: String, // "MI-2026-000125" or "MO-2026-000087"
    val movementType: String, // "IN", "OUT"
    val supplierName: String = "", // Supplier for IN; Destination/Party for OUT
    val destinationParty: String = "",
    val vehicleNumber: String = "",
    val driverName: String = "",
    val driverMobile: String = "",
    val transporter: String = "",
    val invoiceNumber: String = "",
    val invoiceDate: String = "",
    val challanNumber: String = "",
    val poNumber: String = "",
    val ewayBill: String = "",
    val purpose: String = "", // Purpose for IN (e.g. "Delivery", "Repair Return")
    val outReason: String = "", // For OUT: "Repair", "Return to Supplier", "Transfer", "Customer Delivery", "Scrap", "Other"
    val authorization: String = "",
    val referenceDocument: String = "",
    val expectedReturnDate: Long? = null,
    val gate: String = "Main Gate",
    val receivedBy: String = "",
    val recordedBy: String = "Guard (Raju)",
    val remarks: String = "",
    val status: String = "COMPLETED", // "PENDING_APPROVAL", "APPROVED", "REJECTED", "COMPLETED", "SENT_FOR_REPAIR", "RETURNED"
    val linkedMovementId: String? = null,
    val repairId: String? = null,
    val isReturned: Boolean = false,
    val inTime: Long = System.currentTimeMillis(),
    val outTime: Long? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false,
    val syncStatus: String = "PENDING"
) {
    val entryDate: String get() = com.example.util.DateTimeUtils.formatStandardDate(inTime)
    val entryTime: String get() = com.example.util.DateTimeUtils.formatStandardTime(inTime)
    val exitDate: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardDate(it) }
    val exitTime: String? get() = outTime?.let { com.example.util.DateTimeUtils.formatStandardTime(it) }
}

@Entity(tableName = "material_movement_items")
data class MaterialMovementItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val movementId: String, // Links to MaterialMovement.movementId
    val materialDescription: String,
    val itemCode: String = "",
    val quantity: Double,
    val uom: String = "NOS",
    val serialNumber: String = "",
    val assetNumber: String = "",
    val remarks: String = "",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(tableName = "repair_records")
data class RepairRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val repairId: String, // e.g. "REP-2026-000021"
    val movementOutId: String, // Outward movement ID
    val materialDescription: String,
    val sentQuantity: Double,
    val returnedQuantity: Double = 0.0,
    val uom: String = "NOS",
    val assetOrSerial: String = "",
    val repairVendor: String,
    val repairReason: String,
    val repairChallanRef: String = "",
    val authorizedBy: String,
    val sentDate: Long = System.currentTimeMillis(),
    val expectedReturnDate: Long,
    val actualReturnDate: Long? = null,
    val inTime: Long? = actualReturnDate,
    val outTime: Long = sentDate,
    val movementInId: String? = null, // Linked inward movement ID created upon return
    val conditionOnReturn: String = "",
    val returnDocumentRef: String = "",
    val receivedBy: String = "",
    val status: String = "PENDING", // "PENDING", "RETURNED_FROM_REPAIR"
    val remarks: String = "",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
) {
    val exitDate: String get() = com.example.util.DateTimeUtils.formatStandardDate(outTime)
    val exitTime: String get() = com.example.util.DateTimeUtils.formatStandardTime(outTime)
    val entryDate: String? get() = inTime?.let { com.example.util.DateTimeUtils.formatStandardDate(it) }
    val entryTime: String? get() = inTime?.let { com.example.util.DateTimeUtils.formatStandardTime(it) }
}

@Entity(tableName = "material_documents")
data class MaterialDocument(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val site: String = "Delhi Hub",
    val relatedId: String, // movementId or repairId
    val docType: String, // "Invoice", "Challan", "Repair Document", "Return Document", "E-way Bill", "Photo"
    val fileName: String,
    val fileUri: String = "",
    val notes: String = "",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)
