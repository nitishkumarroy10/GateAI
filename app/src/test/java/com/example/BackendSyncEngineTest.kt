package com.example

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Backend Sync Engine Specification & Test Suite for GateAI.
 * Validates:
 * 1. Authentication & Role / Site Isolation
 * 2. Idempotency & Duplicate client_mutation_id Prevention
 * 3. Batch Push Mutation Execution
 * 4. Delta Pull Synchronization
 * 5. Quantity-Sensitive Conflict Detection & Over-Return Rejection
 * 6. Multi-Stage Partial Repair Returns
 * 7. Append-Only Server Audit Logging
 */
class BackendSyncEngineTest {

    data class ServerUser(
        val userId: String,
        val companyId: String,
        val siteId: String,
        val role: String, // "GUARD", "SUPERVISOR", "ADMIN"
        val isActive: Boolean = true
    )

    data class MutationItem(
        val clientMutationId: String,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val payload: Map<String, Any>,
        val clientTimestamp: Long
    )

    data class MutationResult(
        val clientMutationId: String,
        val entityType: String,
        val entityId: String,
        val status: String, // "SUCCESS", "DUPLICATE_IGNORED", "CONFLICT", "ERROR"
        val serverUpdatedTime: Long? = null,
        val errorMessage: String? = null
    )

    data class ServerRepairRecord(
        val repairId: String,
        val siteId: String,
        val materialDescription: String,
        val sentQuantity: Double,
        var returnedQuantity: Double,
        val uom: String,
        val repairVendor: String,
        var status: String,
        var updatedAt: Long
    )

    data class ServerAuditLog(
        val logId: String = UUID.randomUUID().toString(),
        val siteId: String,
        val userId: String,
        val action: String,
        val recordId: String,
        val details: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Backend Cloud Engine State
    class MockCloudBackend {
        val processedMutations = mutableMapOf<String, MutationResult>() // Key: siteId:mutationId -> Result
        val repairs = mutableMapOf<String, ServerRepairRecord>() // Key: siteId:repairId -> Record
        val auditLogs = mutableListOf<ServerAuditLog>()

        fun executePush(
            user: ServerUser,
            requestedSiteId: String,
            mutations: List<MutationItem>
        ): Pair<Int, List<MutationResult>> {
            // 1. Site Authorization Verification
            if (user.siteId != requestedSiteId) {
                return Pair(403, listOf(
                    MutationResult(
                        clientMutationId = "GLOBAL",
                        entityType = "AUTH",
                        entityId = "AUTH",
                        status = "ERROR",
                        errorMessage = "Forbidden: User belongs to site ${user.siteId}, cannot modify site $requestedSiteId"
                    )
                ))
            }

            val results = mutableListOf<MutationResult>()
            val now = System.currentTimeMillis()

            for (m in mutations) {
                val mutationKey = "${user.siteId}:${m.clientMutationId}"

                // 2. Idempotency Check
                if (processedMutations.containsKey(mutationKey)) {
                    results.add(
                        MutationResult(
                            clientMutationId = m.clientMutationId,
                            entityType = m.entityType,
                            entityId = m.entityId,
                            status = "DUPLICATE_IGNORED",
                            serverUpdatedTime = now,
                            errorMessage = "Mutation previously executed"
                        )
                    )
                    continue
                }

                // 3. Process Domain Mutation
                when (m.entityType) {
                    "REPAIR" -> {
                        when (m.operation) {
                            "CREATE" -> {
                                val repKey = "${user.siteId}:${m.entityId}"
                                val sentQty = (m.payload["sentQuantity"] as Number).toDouble()
                                val repair = ServerRepairRecord(
                                    repairId = m.entityId,
                                    siteId = user.siteId,
                                    materialDescription = m.payload["materialDescription"] as String,
                                    sentQuantity = sentQty,
                                    returnedQuantity = 0.0,
                                    uom = m.payload["uom"] as String,
                                    repairVendor = m.payload["repairVendor"] as String,
                                    status = "PENDING",
                                    updatedAt = now
                                )
                                repairs[repKey] = repair

                                // Server-side audit
                                auditLogs.add(
                                    ServerAuditLog(
                                        siteId = user.siteId,
                                        userId = user.userId,
                                        action = "REPAIR_OUT",
                                        recordId = m.entityId,
                                        details = "Dispatched $sentQty ${repair.uom} to ${repair.repairVendor}"
                                    )
                                )

                                val res = MutationResult(
                                    clientMutationId = m.clientMutationId,
                                    entityType = m.entityType,
                                    entityId = m.entityId,
                                    status = "SUCCESS",
                                    serverUpdatedTime = now
                                )
                                processedMutations[mutationKey] = res
                                results.add(res)
                            }
                            "RETURN" -> {
                                val repKey = "${user.siteId}:${m.entityId}"
                                val existing = repairs[repKey]
                                if (existing == null) {
                                    val errRes = MutationResult(
                                        clientMutationId = m.clientMutationId,
                                        entityType = m.entityType,
                                        entityId = m.entityId,
                                        status = "ERROR",
                                        errorMessage = "Repair record not found"
                                    )
                                    processedMutations[mutationKey] = errRes
                                    results.add(errRes)
                                } else {
                                    val returnQty = (m.payload["returnQuantity"] as Number).toDouble()
                                    val newTotalReturned = existing.returnedQuantity + returnQty

                                    // Conflict Detection: Cannot return more than sent
                                    if (newTotalReturned > existing.sentQuantity) {
                                        val remaining = (existing.sentQuantity - existing.returnedQuantity).coerceAtLeast(0.0)
                                        val confRes = MutationResult(
                                            clientMutationId = m.clientMutationId,
                                            entityType = m.entityType,
                                            entityId = m.entityId,
                                            status = "CONFLICT",
                                            errorMessage = "Quantity Conflict: Attempted to return $returnQty ${existing.uom}, but only $remaining ${existing.uom} remains outside."
                                        )
                                        processedMutations[mutationKey] = confRes
                                        results.add(confRes)
                                    } else {
                                        existing.returnedQuantity = newTotalReturned
                                        existing.status = if (newTotalReturned >= existing.sentQuantity) "RETURNED_FROM_REPAIR" else "PARTIALLY_RETURNED"
                                        existing.updatedAt = now

                                        auditLogs.add(
                                            ServerAuditLog(
                                                siteId = user.siteId,
                                                userId = user.userId,
                                                action = "REPAIR_RETURN",
                                                recordId = m.entityId,
                                                details = "Returned $returnQty ${existing.uom}. Total returned: $newTotalReturned/${existing.sentQuantity}. Status: ${existing.status}"
                                            )
                                        )

                                        val okRes = MutationResult(
                                            clientMutationId = m.clientMutationId,
                                            entityType = m.entityType,
                                            entityId = m.entityId,
                                            status = "SUCCESS",
                                            serverUpdatedTime = now
                                        )
                                        processedMutations[mutationKey] = okRes
                                        results.add(okRes)
                                    }
                                }
                            }
                            else -> {
                                val errRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "ERROR", errorMessage = "Unsupported operation")
                                results.add(errRes)
                            }
                        }
                    }
                    "USER" -> {
                        if (user.role != "ADMIN") {
                            val errRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "ERROR", errorMessage = "Forbidden: Only ADMIN can administer users")
                            results.add(errRes)
                        } else {
                            val okRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "SUCCESS", serverUpdatedTime = now)
                            processedMutations[mutationKey] = okRes
                            results.add(okRes)
                        }
                    }
                    "SUPPLIER" -> {
                        if (user.role == "GUARD") {
                            val errRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "ERROR", errorMessage = "Forbidden: GUARD cannot modify suppliers")
                            results.add(errRes)
                        } else {
                            val okRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "SUCCESS", serverUpdatedTime = now)
                            processedMutations[mutationKey] = okRes
                            results.add(okRes)
                        }
                    }
                    "AUDIT_LOG" -> {
                        if (m.operation == "UPDATE" || m.operation == "DELETE") {
                            val errRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "ERROR", errorMessage = "Forbidden: Audit log entries are immutable")
                            results.add(errRes)
                        } else {
                            val okRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "SUCCESS", serverUpdatedTime = now)
                            processedMutations[mutationKey] = okRes
                            results.add(okRes)
                        }
                    }
                    else -> {
                        val okRes = MutationResult(m.clientMutationId, m.entityType, m.entityId, "SUCCESS", serverUpdatedTime = now)
                        processedMutations[mutationKey] = okRes
                        results.add(okRes)
                    }
                }
            }

            return Pair(200, results)
        }

        fun executePull(user: ServerUser, requestedSiteId: String, sinceTimestamp: Long): Pair<Int, List<ServerRepairRecord>> {
            if (user.siteId != requestedSiteId) {
                return Pair(403, emptyList())
            }
            val delta = repairs.values.filter { it.siteId == user.siteId && it.updatedAt >= sinceTimestamp }
            return Pair(200, delta)
        }
    }

    private lateinit var backend: MockCloudBackend
    private lateinit var guardSiteA: ServerUser
    private lateinit var guardSiteB: ServerUser

    @Before
    fun setUp() {
        backend = MockCloudBackend()
        guardSiteA = ServerUser(
            userId = "USR-001",
            companyId = "COMP-01",
            siteId = "SITE-ALPHA",
            role = "GUARD"
        )
        guardSiteB = ServerUser(
            userId = "USR-002",
            companyId = "COMP-01",
            siteId = "SITE-BETA",
            role = "GUARD"
        )
    }

    @Test
    fun testSiteIsolationAndAuthorization() {
        val mutation = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-001",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Electric Motor 50HP",
                "sentQuantity" to 1.0,
                "uom" to "NOS",
                "repairVendor" to "ABB Services"
            ),
            clientTimestamp = System.currentTimeMillis()
        )

        // Guard from Site A attempts to push to Site B
        val (statusCode, results) = backend.executePush(guardSiteA, "SITE-BETA", listOf(mutation))
        assertEquals(403, statusCode)
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Forbidden"))
    }

    @Test
    fun testDuplicateClientMutationIdIdempotency() {
        val mutationId = UUID.randomUUID().toString()
        val mutation = MutationItem(
            clientMutationId = mutationId,
            entityType = "REPAIR",
            entityId = "REP-002",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Centrifugal Pump",
                "sentQuantity" to 1.0,
                "uom" to "NOS",
                "repairVendor" to "Kirloskar Brothers"
            ),
            clientTimestamp = System.currentTimeMillis()
        )

        // First Push -> SUCCESS
        val (code1, results1) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(mutation))
        assertEquals(200, code1)
        assertEquals("SUCCESS", results1.first().status)

        // Second Push with identical clientMutationId -> DUPLICATE_IGNORED
        val (code2, results2) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(mutation))
        assertEquals(200, code2)
        assertEquals("DUPLICATE_IGNORED", results2.first().status)
    }

    @Test
    fun testBatchPushAndPullSynchronization() {
        val m1 = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-010",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Digital Multimeter",
                "sentQuantity" to 2.0,
                "uom" to "NOS",
                "repairVendor" to "Fluke Service"
            ),
            clientTimestamp = System.currentTimeMillis()
        )
        val m2 = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-011",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Air Compressor Valve",
                "sentQuantity" to 5.0,
                "uom" to "NOS",
                "repairVendor" to "Atlas Copco"
            ),
            clientTimestamp = System.currentTimeMillis()
        )

        val (pushCode, pushResults) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m1, m2))
        assertEquals(200, pushCode)
        assertEquals(2, pushResults.size)
        assertTrue(pushResults.all { it.status == "SUCCESS" })

        // Pull delta for Site Alpha
        val (pullCode, delta) = backend.executePull(guardSiteA, "SITE-ALPHA", 0L)
        assertEquals(200, pullCode)
        assertEquals(2, delta.size)

        // Pull delta for Site Beta must return empty (Isolation)
        val (pullBetaCode, deltaBeta) = backend.executePull(guardSiteB, "SITE-BETA", 0L)
        assertEquals(200, pullBetaCode)
        assertEquals(0, deltaBeta.size)
    }

    @Test
    fun testQuantityConflictAndOverReturnRejection() {
        // Step 1: Create repair for 5 Valves
        val createMutation = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-VALVE-01",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Hydraulic Directional Valve",
                "sentQuantity" to 5.0,
                "uom" to "NOS",
                "repairVendor" to "Hydraulics India"
            ),
            clientTimestamp = System.currentTimeMillis()
        )
        backend.executePush(guardSiteA, "SITE-ALPHA", listOf(createMutation))

        // Step 2: Device A returns 2 valves -> SUCCESS (Status: PARTIALLY_RETURNED, remaining 3)
        val return2Mutation = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-VALVE-01",
            operation = "RETURN",
            payload = mapOf("returnQuantity" to 2.0),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, res1) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(return2Mutation))
        assertEquals("SUCCESS", res1.first().status)
        assertEquals("PARTIALLY_RETURNED", backend.repairs["SITE-ALPHA:REP-VALVE-01"]?.status)
        assertEquals(2.0, backend.repairs["SITE-ALPHA:REP-VALVE-01"]?.returnedQuantity ?: 0.0, 0.001)

        // Step 3: Device B attempts to return 4 valves (Remaining is 3) -> CONFLICT (Over-return rejected)
        val return4Mutation = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-VALVE-01",
            operation = "RETURN",
            payload = mapOf("returnQuantity" to 4.0),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, res2) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(return4Mutation))
        assertEquals("CONFLICT", res2.first().status)
        assertTrue(res2.first().errorMessage!!.contains("Quantity Conflict"))

        // State remains at 2.0 returned
        assertEquals(2.0, backend.repairs["SITE-ALPHA:REP-VALVE-01"]?.returnedQuantity ?: 0.0, 0.001)

        // Step 4: Device B returns remaining 3 valves -> SUCCESS (Status: RETURNED_FROM_REPAIR)
        val return3Mutation = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-VALVE-01",
            operation = "RETURN",
            payload = mapOf("returnQuantity" to 3.0),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, res3) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(return3Mutation))
        assertEquals("SUCCESS", res3.first().status)
        assertEquals("RETURNED_FROM_REPAIR", backend.repairs["SITE-ALPHA:REP-VALVE-01"]?.status)
        assertEquals(5.0, backend.repairs["SITE-ALPHA:REP-VALVE-01"]?.returnedQuantity ?: 0.0, 0.001)
    }

    @Test
    fun testServerAppendOnlyAuditLogs() {
        val countBefore = backend.auditLogs.size
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-AUDIT-01",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Laser Sensor",
                "sentQuantity" to 1.0,
                "uom" to "NOS",
                "repairVendor" to "Sick India"
            ),
            clientTimestamp = System.currentTimeMillis()
        )
        backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals(countBefore + 1, backend.auditLogs.size)
        assertEquals("REPAIR_OUT", backend.auditLogs.last().action)
        assertEquals("REP-AUDIT-01", backend.auditLogs.last().recordId)
    }

    @Test
    fun testGuardCannotAdministerUsers() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "USER",
            entityId = "USR-003",
            operation = "CREATE",
            payload = mapOf("role" to "GUARD"),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, results) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Only ADMIN can administer users"))
    }

    @Test
    fun testGuardCannotElevateRole() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "USER",
            entityId = "USR-001",
            operation = "UPDATE",
            payload = mapOf("role" to "ADMIN"),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, results) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Only ADMIN can administer users"))
    }

    @Test
    fun testUnauthorizedSupplierModificationRejected() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "SUPPLIER",
            entityId = "SUP-01",
            operation = "UPDATE",
            payload = mapOf("name" to "New Name"),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, results) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("GUARD cannot modify suppliers"))
    }

    @Test
    fun testAuditUpdateRejected() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "AUDIT_LOG",
            entityId = "LOG-01",
            operation = "UPDATE",
            payload = mapOf("action" to "FAKE_ACTION"),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, results) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Audit log entries are immutable"))
    }

    @Test
    fun testAuditDeleteRejected() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "AUDIT_LOG",
            entityId = "LOG-01",
            operation = "DELETE",
            payload = emptyMap(),
            clientTimestamp = System.currentTimeMillis()
        )
        val (_, results) = backend.executePush(guardSiteA, "SITE-ALPHA", listOf(m))
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Audit log entries are immutable"))
    }

    @Test
    fun testSyncMutationFromAnotherSiteRejected() {
        val m = MutationItem(
            clientMutationId = UUID.randomUUID().toString(),
            entityType = "REPAIR",
            entityId = "REP-020",
            operation = "CREATE",
            payload = mapOf(
                "materialDescription" to "Test",
                "sentQuantity" to 1.0,
                "uom" to "NOS",
                "repairVendor" to "Vendor"
            ),
            clientTimestamp = System.currentTimeMillis()
        )
        // Guard A is from SITE-ALPHA, but attempts to push to SITE-BETA
        val (code, results) = backend.executePush(guardSiteA, "SITE-BETA", listOf(m))
        assertEquals(403, code)
        assertEquals("ERROR", results.first().status)
        assertTrue(results.first().errorMessage!!.contains("Forbidden"))
    }
}
