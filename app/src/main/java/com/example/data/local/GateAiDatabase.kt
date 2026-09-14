package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS suppliers (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                supplierName TEXT NOT NULL,
                contactPerson TEXT NOT NULL,
                mobile TEXT NOT NULL,
                email TEXT NOT NULL,
                address TEXT NOT NULL,
                gstin TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS materials_master (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                materialName TEXT NOT NULL,
                itemCode TEXT NOT NULL,
                category TEXT NOT NULL,
                uom TEXT NOT NULL,
                isSerialised INTEGER NOT NULL,
                isAsset INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS material_movements (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                movementId TEXT NOT NULL,
                movementType TEXT NOT NULL,
                supplierName TEXT NOT NULL,
                destinationParty TEXT NOT NULL,
                vehicleNumber TEXT NOT NULL,
                driverName TEXT NOT NULL,
                driverMobile TEXT NOT NULL,
                transporter TEXT NOT NULL,
                invoiceNumber TEXT NOT NULL,
                invoiceDate TEXT NOT NULL,
                challanNumber TEXT NOT NULL,
                poNumber TEXT NOT NULL,
                ewayBill TEXT NOT NULL,
                purpose TEXT NOT NULL,
                outReason TEXT NOT NULL,
                authorization TEXT NOT NULL,
                referenceDocument TEXT NOT NULL,
                expectedReturnDate INTEGER,
                gate TEXT NOT NULL,
                receivedBy TEXT NOT NULL,
                recordedBy TEXT NOT NULL,
                remarks TEXT NOT NULL,
                status TEXT NOT NULL,
                linkedMovementId TEXT,
                repairId TEXT,
                isReturned INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL,
                syncStatus TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS material_movement_items (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                movementId TEXT NOT NULL,
                materialDescription TEXT NOT NULL,
                itemCode TEXT NOT NULL,
                quantity REAL NOT NULL,
                uom TEXT NOT NULL,
                serialNumber TEXT NOT NULL,
                assetNumber TEXT NOT NULL,
                remarks TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS repair_records (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                repairId TEXT NOT NULL,
                movementOutId TEXT NOT NULL,
                materialDescription TEXT NOT NULL,
                sentQuantity REAL NOT NULL,
                returnedQuantity REAL NOT NULL,
                uom TEXT NOT NULL,
                assetOrSerial TEXT NOT NULL,
                repairVendor TEXT NOT NULL,
                repairReason TEXT NOT NULL,
                repairChallanRef TEXT NOT NULL,
                authorizedBy TEXT NOT NULL,
                sentDate INTEGER NOT NULL,
                expectedReturnDate INTEGER NOT NULL,
                actualReturnDate INTEGER,
                movementInId TEXT,
                conditionOnReturn TEXT NOT NULL,
                returnDocumentRef TEXT NOT NULL,
                receivedBy TEXT NOT NULL,
                status TEXT NOT NULL,
                remarks TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS material_documents (
                id TEXT NOT NULL PRIMARY KEY,
                site TEXT NOT NULL,
                relatedId TEXT NOT NULL,
                docType TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileUri TEXT NOT NULL,
                notes TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE material_movements ADD COLUMN inTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE material_movements ADD COLUMN outTime INTEGER")
        db.execSQL("UPDATE material_movements SET inTime = created_at")
        db.execSQL("ALTER TABLE repair_records ADD COLUMN inTime INTEGER")
        db.execSQL("ALTER TABLE repair_records ADD COLUMN outTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE repair_records SET outTime = sentDate, inTime = actualReturnDate")
    }
}

@Database(
    entities = [
        Vehicle::class,
        Visitor::class,
        VehicleEntry::class,
        VisitorEntry::class,
        Alert::class,
        AuditLog::class,
        Supplier::class,
        MaterialMaster::class,
        MaterialMovement::class,
        MaterialMovementItem::class,
        RepairRecord::class,
        MaterialDocument::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GateAiDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun visitorDao(): VisitorDao
    abstract fun vehicleEntryDao(): VehicleEntryDao
    abstract fun visitorEntryDao(): VisitorEntryDao
    abstract fun alertDao(): AlertDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun supplierDao(): SupplierDao
    abstract fun materialMasterDao(): MaterialMasterDao
    abstract fun materialMovementDao(): MaterialMovementDao
    abstract fun materialMovementItemDao(): MaterialMovementItemDao
    abstract fun repairRecordDao(): RepairRecordDao
    abstract fun materialDocumentDao(): MaterialDocumentDao

    companion object {
        @Volatile
        private var Instance: GateAiDatabase? = null

        fun getDatabase(context: Context): GateAiDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    GateAiDatabase::class.java,
                    "gateai_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
                .also { Instance = it }
            }
        }
    }
}
