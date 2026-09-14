package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: GateAiDatabase
    private lateinit var repository: GateAiRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GateAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GateAiRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun read_string_from_context() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GateAI", appName)
    }

    @Test
    fun vehicleIn_and_preventDuplicate_test() = runBlocking {
        val entry = VehicleEntry(
            vehicleNumber = "DL01AB1234",
            vehicleType = "Truck",
            driverName = "Ramesh Kumar",
            purpose = "Delivery",
            openingKm = 50000
        )

        val result1 = repository.vehicleIn(entry)
        assertTrue("First vehicle IN should succeed", result1 is GateResult.Success)

        // Attempt duplicate IN
        val duplicateEntry = VehicleEntry(
            vehicleNumber = "DL01AB1234",
            vehicleType = "Truck",
            driverName = "Another Driver",
            purpose = "Pickup",
            openingKm = 50010
        )
        val result2 = repository.vehicleIn(duplicateEntry)
        assertTrue("Duplicate vehicle IN must be blocked", result2 is GateResult.Error)

        // Check active alert generated for duplicate attempt
        val alerts = repository.activeAlerts.first()
        assertTrue("Duplicate alert should be generated", alerts.any { it.type == "Duplicate Entry" })
    }

    @Test
    fun vehicleOut_blockLowerKm_and_auditLog_test() = runBlocking {
        val entry = VehicleEntry(
            vehicleNumber = "HR26BC9999",
            vehicleType = "Tempo",
            driverName = "Suresh",
            purpose = "Material Movement",
            openingKm = 20000
        )
        val inResult = repository.vehicleIn(entry) as GateResult.Success

        // Attempt vehicle OUT with closing KM < opening KM (19500 < 20000)
        val outResultLower = repository.vehicleOut(
            entryId = inResult.data.entryId,
            closingKm = 19500
        )
        assertTrue("Closing KM lower than opening KM must be blocked", outResultLower is GateResult.Error)

        // Valid vehicle OUT
        val outResultValid = repository.vehicleOut(
            entryId = inResult.data.entryId,
            closingKm = 20050
        )
        assertTrue("Valid closing KM should succeed", outResultValid is GateResult.Success)

        // Verify Audit Log generated
        val logs = repository.auditLogs.first()
        assertTrue("Audit log should record vehicle OUT", logs.any { it.action == "VEHICLE_OUT" })
    }

    @Test
    fun visitorIn_and_checkout_test() = runBlocking {
        val visitor = VisitorEntry(
            visitorName = "Rahul Verma",
            mobileNumber = "9876543210",
            company = "Acme Corp",
            host = "Accounts Dept",
            purpose = "Meeting"
        )
        val inRes = repository.visitorIn(visitor) as GateResult.Success
        assertNotNull("Visitor pass ID generated", inRes.data.passId)

        // Checkout visitor
        val outRes = repository.visitorOut(inRes.data.passId)
        assertTrue("Visitor OUT should succeed", outRes is GateResult.Success)
    }

    @Test
    fun vehicleWorkflow_unusualKm_and_supervisorOverride_test() = runBlocking {
        // Vehicle IN with 10,000 KM
        val entry = VehicleEntry(
            vehicleNumber = "UP16XY1234",
            vehicleType = "Truck",
            driverName = "Manoj Singh",
            purpose = "Stock Delivery",
            openingKm = 10000
        )
        val inResult = repository.vehicleIn(entry, currentUser = "Guard Raju", currentUserRole = "GUARD") as GateResult.Success

        // Attempt Vehicle OUT with 10,500 KM (+500 KM difference > 300 KM limit) without supervisor override
        val outUnusualNoOverride = repository.vehicleOut(
            entryId = inResult.data.entryId,
            closingKm = 10500,
            supervisorOverride = false,
            currentUser = "Guard Raju",
            currentUserRole = "GUARD"
        )
        assertTrue("Unusual KM without override should fail", outUnusualNoOverride is GateResult.Error)

        // Attempt Vehicle OUT with Supervisor Override approved
        val outWithOverride = repository.vehicleOut(
            entryId = inResult.data.entryId,
            closingKm = 10500,
            supervisorOverride = true,
            supervisorNotes = "Approved diversion due to highway roadblock",
            currentUser = "Supervisor Vikram",
            currentUserRole = "SUPERVISOR"
        )
        assertTrue("Unusual KM with supervisor override should succeed", outWithOverride is GateResult.Success)
        val completed = (outWithOverride as GateResult.Success).data
        assertEquals("Completed", completed.status)
        assertTrue(completed.supervisorOverride)
        assertEquals(10500, completed.closingKm)
        assertEquals(500, completed.closingKm!! - completed.openingKm)

        // Verify Audit Log captured the override
        val logs = repository.auditLogs.first()
        assertTrue("Audit log should record supervisor override", logs.any { it.action == "SUPERVISOR_OVERRIDE" })
    }

    @Test
    fun visitorWorkflow_status_duration_and_auditLog_test() = runBlocking {
        val visitor = VisitorEntry(
            visitorName = "Pooja Sharma",
            mobileNumber = "9988776655",
            company = "Consulting Partners",
            host = "HR Dept",
            purpose = "Interview",
            vehicleNumber = "DL02CD5678"
        )
        val inRes = repository.visitorIn(visitor, currentUser = "Guard Raju", currentUserRole = "GUARD") as GateResult.Success
        val passId = inRes.data.passId

        // Verify inside state
        val insideList = repository.insideVisitors.first()
        assertTrue("Visitor should be in inside list", insideList.any { it.passId == passId })

        // Check out visitor
        val outRes = repository.visitorOut(passId, currentUser = "Guard Raju", currentUserRole = "GUARD")
        assertTrue("Visitor OUT should succeed", outRes is GateResult.Success)
        val checkedOut = (outRes as GateResult.Success).data
        assertEquals("Completed", checkedOut.status)
        assertNotNull(checkedOut.outTime)
        assertTrue(checkedOut.outTime!! >= checkedOut.inTime)

        // Verify removed from inside list
        val insideAfter = repository.insideVisitors.first()
        assertFalse("Visitor should no longer be inside", insideAfter.any { it.passId == passId })

        // Verify audit log
        val logs = repository.auditLogs.first()
        assertTrue("Audit log should record visitor entry and exit", logs.any { it.action == "VISITOR_OUT" && it.recordId == inRes.data.entryId })
    }

    @Test
    fun materialInWorkflow_multiItem_and_traceability_test() = runBlocking {
        val movement = MaterialMovement(
            movementId = "",
            movementType = "IN",
            supplierName = "Tata Steel Ltd",
            vehicleNumber = "HR55AB1122",
            invoiceNumber = "INV-2026-9901",
            challanNumber = "CH-7788",
            poNumber = "PO-8812",
            ewayBill = "EWB-1029384756",
            purpose = "Raw Material Delivery",
            driverName = "Balwinder Singh",
            driverMobile = "9811223344",
            transporter = "ABC Logistics",
            receivedBy = "Warehouse Manager",
            gate = "Main Gate"
        )

        val items = listOf(
            MaterialMovementItem(
                movementId = "",
                materialDescription = "Cold Rolled Steel Coils",
                itemCode = "STL-CR-01",
                quantity = 5.0,
                uom = "TON",
                serialNumber = "COIL-A-101"
            ),
            MaterialMovementItem(
                movementId = "",
                materialDescription = "Galvanized Sheets 2mm",
                itemCode = "STL-GI-02",
                quantity = 120.0,
                uom = "NOS",
                serialNumber = "PLT-9921"
            )
        )

        val inResult = repository.materialIn(movement, items, null, currentUser = "Guard Raju", currentUserRole = "GUARD")
        assertTrue("Material IN should succeed", inResult is GateResult.Success)
        val created = (inResult as GateResult.Success).data
        assertTrue("Movement ID should be generated", created.movementId.startsWith("MI-"))

        // Verify items stored
        val storedItems = repository.getItemsForMovement(created.movementId)
        assertEquals(2, storedItems.size)
        assertEquals(5.0, storedItems[0].quantity, 0.001)
        assertEquals("TON", storedItems[0].uom)

        // Verify traceability by invoice number
        val searchByInvoice = repository.searchMaterialTraceability("INV-2026-9901")
        assertEquals(1, searchByInvoice.size)
        assertEquals(created.movementId, searchByInvoice[0].first.movementId)

        // Verify traceability by serial number
        val searchBySerial = repository.searchMaterialTraceability("COIL-A-101")
        assertEquals(1, searchBySerial.size)
        assertEquals(created.movementId, searchBySerial[0].first.movementId)
    }

    @Test
    fun materialOut_repairLifecycle_and_return_test() = runBlocking {
        // Step 1: Material OUT for Repair
        val outMovement = MaterialMovement(
            movementId = "",
            movementType = "OUT",
            destinationParty = "Precise Motor Works",
            outReason = "Repair",
            authorization = "Plant Head Mr. Saxena",
            referenceDocument = "RGP-4012",
            vehicleNumber = "DL04CD9012",
            driverName = "Harish"
        )

        val repairItem = listOf(
            MaterialMovementItem(
                movementId = "",
                materialDescription = "50HP Induction Motor",
                itemCode = "MTR-IND-50",
                quantity = 1.0,
                uom = "NOS",
                serialNumber = "MTR-SN-88219"
            )
        )

        val repairData = RepairData(
            repairVendor = "Precise Motor Works",
            repairReason = "Stator winding burnt",
            assetOrSerial = "MTR-SN-88219",
            expectedReturnDate = System.currentTimeMillis() + (7 * 24 * 3600 * 1000L),
            repairChallanRef = "RGP-4012",
            authorizedBy = "Plant Head Mr. Saxena"
        )

        val outRes = repository.materialOut(outMovement, repairItem, repairData, null, currentUser = "Guard Raju", currentUserRole = "GUARD")
        assertTrue("Material OUT for repair should succeed", outRes is GateResult.Success)
        val createdOut = (outRes as GateResult.Success).data
        val repId = createdOut.repairId
        assertNotNull("Repair ID must be assigned", repId)
        assertTrue(repId!!.startsWith("REP-"))

        // Verify Pending Repairs list
        val pendingList = repository.pendingRepairs.first()
        assertTrue("Motor should appear in pending repairs", pendingList.any { it.repairId == repId })

        // Step 2: Return from Repair
        val returnRes = repository.returnFromRepair(
            repairId = repId,
            actualReturnedQty = 1.0,
            condition = "Tested OK / Rewound",
            returnDocRef = "DC-MOTOR-110",
            receivedBy = "Maintenance Lead",
            remarks = "Installed and test run successful",
            currentUser = "Guard Raju",
            currentUserRole = "GUARD"
        )
        assertTrue("Return from repair should succeed", returnRes is GateResult.Success)
        val returnedRep = (returnRes as GateResult.Success).data
        assertEquals("RETURNED_FROM_REPAIR", returnedRep.status)
        assertEquals(1.0, returnedRep.returnedQuantity, 0.001)
        assertNotNull(returnedRep.actualReturnDate)

        // Verify no longer pending
        val pendingAfter = repository.pendingRepairs.first()
        assertFalse("Motor should no longer be pending repair", pendingAfter.any { it.repairId == repId })
    }

    @Test
    fun materialOut_supplierReturn_test() = runBlocking {
        val movement = MaterialMovement(
            movementId = "",
            movementType = "OUT",
            destinationParty = "Acme Fasteners Pvt Ltd",
            outReason = "Return to Supplier",
            authorization = "Quality Lead",
            referenceDocument = "DN-8810",
            remarks = "[Defective / Damaged] Thread dimensions out of tolerance"
        )

        val items = listOf(
            MaterialMovementItem(
                movementId = "",
                materialDescription = "M12 Hex Bolts High Tensile",
                itemCode = "BLT-M12-HT",
                quantity = 500.0,
                uom = "NOS"
            )
        )

        val res = repository.materialOut(movement, items, null, null, currentUser = "Guard Raju", currentUserRole = "GUARD")
        assertTrue("Supplier return should succeed", res is GateResult.Success)
        val created = (res as GateResult.Success).data
        assertEquals("COMPLETED", created.status)
        assertEquals("Return to Supplier", created.outReason)
    }

    @Test
    fun unifiedSearch_and_aiAssistant_test() = runBlocking {
        // Seed a vehicle and visitor
        val v = VehicleEntry(
            vehicleNumber = "HR29ZZ1111",
            vehicleType = "Trailer",
            driverName = "Kishan Lal",
            purpose = "Dispatch",
            openingKm = 80000
        )
        repository.vehicleIn(v, currentUser = "Guard", currentUserRole = "GUARD")

        val vis = VisitorEntry(
            visitorName = "Amitabh Gupta",
            mobileNumber = "9123456780",
            company = "Ernst & Young",
            host = "Finance VP",
            purpose = "Statutory Audit"
        )
        val visRes = (repository.visitorIn(vis, currentUser = "Guard", currentUserRole = "GUARD") as GateResult.Success).data

        // Test search
        val vResults = repository.searchVehicles("HR29ZZ1111")
        assertEquals(1, vResults.size)
        assertEquals("HR29ZZ1111", vResults[0].vehicleNumber)

        val visResults = repository.searchVisitors("Amitabh")
        assertEquals(1, visResults.size)
        assertEquals(visRes.passId, visResults[0].passId)

        // Test AI Assistant grounded responses
        val aiService = com.example.ai.GateAiAssistantService()
        val allV = repository.allVehicles.first()
        val allVis = repository.allVisitors.first()
        val alerts = repository.activeAlerts.first()

        val whoIsInsideResponse = aiService.answerQuery(
            query = "Who is currently inside?",
            vehicles = allV,
            visitors = allVis,
            alerts = alerts
        )
        assertTrue(whoIsInsideResponse.contains("HR29ZZ1111"))
        assertTrue(whoIsInsideResponse.contains("Amitabh Gupta"))

        val todaySummaryResponse = aiService.answerQuery(
            query = "Give today's summary.",
            vehicles = allV,
            visitors = allVis,
            alerts = alerts
        )
        assertTrue(todaySummaryResponse.contains("Gate Operations Summary"))
    }

    @Test
    fun repairLifecycle_multiStage_partialReturns_and_exceedValidation_test() = runBlocking {
        // Step 1: Outward repair for 5 Hydraulic Valves
        val outMovement = MaterialMovement(
            movementId = "",
            movementType = "OUT",
            destinationParty = "Hydraulics India Tech",
            outReason = "Repair",
            authorization = "Plant Engineer",
            referenceDocument = "RGP-5055"
        )
        val repairItem = listOf(
            MaterialMovementItem(
                movementId = "",
                materialDescription = "Hydraulic Directional Valve",
                itemCode = "HYD-VLV-01",
                quantity = 5.0,
                uom = "NOS",
                serialNumber = "HYD-BATCH-01"
            )
        )
        val repairData = RepairData(
            repairVendor = "Hydraulics India Tech",
            repairReason = "Pressure seal leak",
            assetOrSerial = "HYD-BATCH-01",
            expectedReturnDate = System.currentTimeMillis() + (5 * 24 * 3600 * 1000L),
            repairChallanRef = "RGP-5055",
            authorizedBy = "Plant Engineer"
        )

        val outRes = repository.materialOut(outMovement, repairItem, repairData, null) as GateResult.Success
        val repId = outRes.data.repairId!!

        // Step 2: First Partial Return of 2 Valves
        val part1Res = repository.returnFromRepair(
            repairId = repId,
            actualReturnedQty = 2.0,
            condition = "Repaired / Tested OK",
            returnDocRef = "DC-PART-1",
            receivedBy = "Maintenance Store",
            remarks = "2 received, 3 pending"
        )
        assertTrue("Partial return 1 should succeed", part1Res is GateResult.Success)
        val repAfterPart1 = (part1Res as GateResult.Success).data
        assertEquals("PARTIALLY_RETURNED", repAfterPart1.status)
        assertEquals(2.0, repAfterPart1.returnedQuantity, 0.001)

        // Verify still in pending list
        val pending1 = repository.pendingRepairs.first()
        assertTrue("Repair should still be in pending list", pending1.any { it.repairId == repId })

        // Step 3: Attempt to return 4 valves (Remaining is 3, so 4 should fail)
        val exceedRes = repository.returnFromRepair(
            repairId = repId,
            actualReturnedQty = 4.0,
            condition = "Tested OK",
            returnDocRef = "DC-EXCEED",
            receivedBy = "Store"
        )
        assertTrue("Exceeding remaining quantity should be rejected", exceedRes is GateResult.Error)

        // Step 4: Second Partial Return of remaining 3 Valves
        val part2Res = repository.returnFromRepair(
            repairId = repId,
            actualReturnedQty = 3.0,
            condition = "Tested OK",
            returnDocRef = "DC-PART-2",
            receivedBy = "Maintenance Store",
            remarks = "Remaining 3 received"
        )
        assertTrue("Partial return 2 should succeed", part2Res is GateResult.Success)
        val repAfterPart2 = (part2Res as GateResult.Success).data
        assertEquals("RETURNED_FROM_REPAIR", repAfterPart2.status)
        assertEquals(5.0, repAfterPart2.returnedQuantity, 0.001)

        // Verify removed from pending list
        val pending2 = repository.pendingRepairs.first()
        assertFalse("Repair should no longer be pending", pending2.any { it.repairId == repId })

        // Step 5: Attempting return after full completion must fail
        val afterFullRes = repository.returnFromRepair(
            repairId = repId,
            actualReturnedQty = 1.0,
            condition = "Tested OK",
            returnDocRef = "DC-LATE",
            receivedBy = "Store"
        )
        assertTrue("Return on completed repair should be rejected", afterFullRes is GateResult.Error)
    }

    @Test
    fun repair_overdue_alerts_test() = runBlocking {
        val outMovement = MaterialMovement(
            movementId = "",
            movementType = "OUT",
            destinationParty = "Apex Calibration Labs",
            outReason = "Repair",
            authorization = "QA Lead",
            referenceDocument = "RGP-CAL-99"
        )
        val repairItem = listOf(
            MaterialMovementItem(
                movementId = "",
                materialDescription = "Digital Caliper Master",
                itemCode = "CAL-MST-01",
                quantity = 1.0,
                uom = "NOS"
            )
        )
        val initialFutureDate = System.currentTimeMillis() + (7 * 24 * 3600 * 1000L)
        val repairData = RepairData(
            repairVendor = "Apex Calibration Labs",
            repairReason = "Yearly Recalibration",
            assetOrSerial = "CAL-9912",
            expectedReturnDate = initialFutureDate,
            repairChallanRef = "RGP-CAL-99",
            authorizedBy = "QA Lead"
        )

        val outRes = repository.materialOut(outMovement, repairItem, repairData, null) as GateResult.Success
        val repId = outRes.data.repairId!!

        // Simulate time passing: update expectedReturnDate to the past in database
        val pastDate = System.currentTimeMillis() - (3 * 24 * 3600 * 1000L) // 3 days ago
        val existingRepair = database.repairRecordDao().getRepairById(repId)!!
        database.repairRecordDao().updateRepair(existingRepair.copy(expectedReturnDate = pastDate))

        // Trigger overdue check
        repository.checkAndTriggerOverdueAlerts()

        val alerts = repository.activeAlerts.first()
        assertTrue("Overdue repair alert must be triggered", alerts.any { it.relatedId == repId && it.type == "Repair Overdue" })
    }
}
