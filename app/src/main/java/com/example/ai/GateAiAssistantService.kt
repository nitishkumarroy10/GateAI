package com.example.ai

import com.example.BuildConfig
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * GateAI Assistant: Grounded Database Context Engine + Zero Hallucination.
 * Answers operational questions regarding Vehicles, Visitors, Materials, and Repairs
 * based strictly on real database records.
 */
class GateAiAssistantService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun answerQuery(
        query: String,
        vehicles: List<VehicleEntry>,
        visitors: List<VisitorEntry>,
        alerts: List<Alert>,
        movements: List<MaterialMovement> = emptyList(),
        repairs: List<RepairRecord> = emptyList(),
        suppliers: List<Supplier> = emptyList()
    ): String = withContext(Dispatchers.Default) {
        val q = query.trim().lowercase()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // ----------------------------------------------------
        // PHASE 3: MATERIAL & REPAIR QUESTIONS
        // ----------------------------------------------------

        // "Which materials are currently outside?"
        if (q.contains("currently outside") || q.contains("materials outside") || q.contains("material outside")) {
            val outside = movements.filter { it.movementType == "OUT" && it.expectedReturnDate != null && !it.isReturned }
            return@withContext if (outside.isNotEmpty()) {
                val list = outside.joinToString("\n") { m ->
                    val isOverdue = m.expectedReturnDate != null && m.expectedReturnDate < System.currentTimeMillis()
                    val expDateStr = m.expectedReturnDate?.let { dateFormat.format(Date(it)) } ?: "N/A"
                    "• ${m.movementId}: Destination ${m.destinationParty.ifBlank { m.supplierName }} [${m.outReason}] - Expected: $expDateStr ${if (isOverdue) "(OVERDUE)" else ""}"
                }
                "Materials Currently Outside (${outside.size} Movements):\n$list"
            } else {
                "No materials are currently marked as outside with pending return."
            }
        }

        // "Which materials are under repair?" or "sent for repair but not returned"
        if (q.contains("under repair") || (q.contains("repair") && (q.contains("not returned") || q.contains("pending")))) {
            val pendingRep = repairs.filter { (it.status == "PENDING" || it.status == "PARTIALLY_RETURNED") && (it.sentQuantity - it.returnedQuantity) > 0 }
            return@withContext if (pendingRep.isNotEmpty()) {
                val list = pendingRep.joinToString("\n") { r ->
                    val isOverdue = r.expectedReturnDate < System.currentTimeMillis()
                    val expStr = dateFormat.format(Date(r.expectedReturnDate))
                    val remaining = r.sentQuantity - r.returnedQuantity
                    val partStr = if (r.status == "PARTIALLY_RETURNED") " [Partially Returned: ${r.returnedQuantity}/${r.sentQuantity} ${r.uom}]" else ""
                    "• ${r.repairId}: ${r.materialDescription} ($remaining/${r.sentQuantity} ${r.uom} outside) with ${r.repairVendor}$partStr - Exp: $expStr ${if (isOverdue) "⚠ OVERDUE" else ""}"
                }
                "Materials Currently Under Repair (${pendingRep.size}):\n$list"
            } else {
                "No materials are currently pending return from repair."
            }
        }

        // "Which repair items are overdue?"
        if (q.contains("repair") && q.contains("overdue")) {
            val now = System.currentTimeMillis()
            val overdueRep = repairs.filter { (it.status == "PENDING" || it.status == "PARTIALLY_RETURNED") && it.expectedReturnDate < now && (it.sentQuantity - it.returnedQuantity) > 0 }
            return@withContext if (overdueRep.isNotEmpty()) {
                val list = overdueRep.joinToString("\n") { r ->
                    val overdueDays = (now - r.expectedReturnDate) / (1000 * 60 * 60 * 24)
                    val remaining = r.sentQuantity - r.returnedQuantity
                    "• ${r.repairId}: ${r.materialDescription} ($remaining/${r.sentQuantity} ${r.uom}) with ${r.repairVendor} (Overdue by $overdueDays days, Expected ${dateFormat.format(Date(r.expectedReturnDate))})"
                }
                "⚠ Overdue Repair Records (${overdueRep.size}):\n$list"
            } else {
                "There are no overdue repair records. All repairs are within their scheduled return windows."
            }
        }

        // "How much material is currently with XYZ Engineering?" or specific vendor query
        if (q.contains("currently with") || q.contains("material with")) {
            val vendorName = suppliers.map { it.supplierName }
                .firstOrNull { q.contains(it.lowercase()) }
                ?: repairs.map { it.repairVendor }.firstOrNull { q.contains(it.lowercase()) }

            if (vendorName != null) {
                val vendorRepairs = repairs.filter { it.repairVendor.equals(vendorName, ignoreCase = true) && (it.status == "PENDING" || it.status == "PARTIALLY_RETURNED") && (it.sentQuantity - it.returnedQuantity) > 0 }
                val vendorMovements = movements.filter {
                    (it.destinationParty.equals(vendorName, ignoreCase = true) || it.supplierName.equals(vendorName, ignoreCase = true)) &&
                            it.movementType == "OUT" && !it.isReturned
                }
                if (vendorRepairs.isNotEmpty() || vendorMovements.isNotEmpty()) {
                    val repList = vendorRepairs.joinToString("\n") { "• Repair #${it.repairId}: ${it.materialDescription} (${it.sentQuantity - it.returnedQuantity}/${it.sentQuantity} ${it.uom} remaining outside)" }
                    val movList = vendorMovements.joinToString("\n") { "• Movement #${it.movementId}: Reason ${it.outReason}" }
                    return@withContext "Material currently with $vendorName:\n$repList\n$movList".trim()
                } else {
                    return@withContext "There is no outstanding material currently with $vendorName."
                }
            }
        }

        // "Show invoice INV-4587 movement history." or search by invoice
        if (q.contains("invoice") || q.contains("challan") || q.contains("inv-")) {
            val words = query.split(" ", "-", "#", ":").map { it.trim().uppercase() }
            val matchedMovement = movements.firstOrNull { m ->
                words.any { w -> w.length > 2 && (m.invoiceNumber.uppercase().contains(w) || m.challanNumber.uppercase().contains(w) || m.movementId.uppercase().contains(w)) }
            }
            if (matchedMovement != null) {
                val repairInfo = if (matchedMovement.repairId != null) "\n• Linked Repair: ${matchedMovement.repairId}" else ""
                val linkedIn = if (matchedMovement.linkedMovementId != null) "\n• Linked Inward ID: ${matchedMovement.linkedMovementId}" else ""
                return@withContext """
                    Movement Record for Invoice/Ref: ${matchedMovement.invoiceNumber.ifBlank { matchedMovement.challanNumber }}:
                    • Movement ID: ${matchedMovement.movementId} (${matchedMovement.movementType})
                    • Party / Supplier: ${matchedMovement.supplierName.ifBlank { matchedMovement.destinationParty }}
                    • Vehicle: ${matchedMovement.vehicleNumber} (Gate: ${matchedMovement.gate})
                    • Status: ${matchedMovement.status}$repairInfo$linkedIn
                    • Date: ${dateFormat.format(Date(matchedMovement.created_at))} at ${timeFormat.format(Date(matchedMovement.created_at))}
                """.trimIndent()
            }
        }

        // "Which supplier sent the most material this month?"
        if (q.contains("supplier") && (q.contains("most") || q.contains("highest"))) {
            val inMovements = movements.filter { it.movementType == "IN" && it.supplierName.isNotBlank() }
            val topSupplier = inMovements.groupBy { it.supplierName }.maxByOrNull { it.value.size }
            return@withContext if (topSupplier != null) {
                "The most active supplier is ${topSupplier.key} with ${topSupplier.value.size} inward consignment(s) recorded."
            } else {
                "No supplier inward consignments have been recorded yet."
            }
        }

        // "How many material movements happened today?"
        if (q.contains("material") && (q.contains("movements happened today") || q.contains("movement today") || q.contains("movements today"))) {
            val todayIn = movements.count { it.movementType == "IN" && it.created_at >= todayStart }
            val todayOut = movements.count { it.movementType == "OUT" && it.created_at >= todayStart }
            return@withContext "Today, $todayIn material inward(s) and $todayOut material outward(s) have been logged at the gate (Total: ${todayIn + todayOut})."
        }

        // "Which items were returned from repair today?"
        if (q.contains("returned from repair today") || (q.contains("returned") && q.contains("repair") && q.contains("today"))) {
            val returnedToday = repairs.filter { it.actualReturnDate != null && it.actualReturnDate!! >= todayStart }
            return@withContext if (returnedToday.isNotEmpty()) {
                val list = returnedToday.joinToString("\n") { "• ${it.repairId}: ${it.materialDescription} (${it.returnedQuantity} ${it.uom}) from ${it.repairVendor}" }
                "Items returned from repair today (${returnedToday.size}):\n$list"
            } else {
                "No items have been returned from repair today."
            }
        }

        // ----------------------------------------------------
        // VEHICLES & VISITORS QUESTIONS
        // ----------------------------------------------------

        // "How many vehicles entered today?"
        if (q.contains("how many vehicle") || (q.contains("vehicle") && q.contains("entered today"))) {
            val count = vehicles.count { it.inTime >= todayStart }
            val inside = vehicles.count { it.status == "Inside" }
            return@withContext "Today, $count vehicle(s) entered the premises. Currently, $inside vehicle(s) are still inside."
        }

        // "Who is currently inside?"
        if (q.contains("who is currently inside") || q.contains("who is inside") || (q.contains("currently inside") && !q.contains("material"))) {
            val insideV = vehicles.filter { it.status == "Inside" }
            val insideVis = visitors.filter { it.status == "Inside" }

            if (insideV.isEmpty() && insideVis.isEmpty()) {
                return@withContext "There are currently no vehicles or visitors inside the premises."
            }

            val sb = StringBuilder()
            sb.append("Currently Inside (${insideV.size} Vehicles, ${insideVis.size} Visitors):\n\n")
            if (insideV.isNotEmpty()) {
                sb.append("🚚 Vehicles:\n")
                insideV.forEach {
                    val duration = formatDuration(System.currentTimeMillis() - it.inTime)
                    sb.append("• ${it.vehicleNumber} (${it.driverName}) - In since ${timeFormat.format(Date(it.inTime))} ($duration)\n")
                }
            }
            if (insideVis.isNotEmpty()) {
                sb.append("\n👤 Visitors:\n")
                insideVis.forEach {
                    val duration = formatDuration(System.currentTimeMillis() - it.inTime)
                    sb.append("• ${it.visitorName} [${it.company.ifEmpty { "Visitor" }}] meeting ${it.host} ($duration)\n")
                }
            }
            return@withContext sb.toString().trim()
        }

        // "Which vehicle has stayed the longest?"
        if (q.contains("longest") && q.contains("vehicle")) {
            val insideV = vehicles.filter { it.status == "Inside" }.minByOrNull { it.inTime }
            return@withContext if (insideV != null) {
                val duration = formatDuration(System.currentTimeMillis() - insideV.inTime)
                "The vehicle that has stayed the longest is ${insideV.vehicleNumber} (Driver: ${insideV.driverName}), inside for $duration (entered at ${timeFormat.format(Date(insideV.inTime))})."
            } else {
                "No vehicles are currently inside."
            }
        }

        // "Which visitors haven't checked out?"
        if ((q.contains("visitor") && q.contains("haven't checked out")) || (q.contains("visitor") && q.contains("not checked out"))) {
            val insideVis = visitors.filter { it.status == "Inside" }
            return@withContext if (insideVis.isNotEmpty()) {
                val list = insideVis.joinToString("\n") { "• ${it.visitorName} (Pass #${it.passId}) - Host: ${it.host}" }
                "The following ${insideVis.size} visitor(s) have not checked out yet:\n$list"
            } else {
                "All visitors have checked out. No visitors are currently inside."
            }
        }

        // "Show today's visitors."
        if (q.contains("today") && q.contains("visitor")) {
            val todayVisitors = visitors.filter { it.inTime >= todayStart }
            return@withContext if (todayVisitors.isNotEmpty()) {
                val list = todayVisitors.joinToString("\n") {
                    "• ${it.visitorName} (${it.company.ifEmpty { "Personal" }}) - Status: ${it.status} (In: ${timeFormat.format(Date(it.inTime))})"
                }
                "Today's Visitors (${todayVisitors.size}):\n$list"
            } else {
                "No visitors have registered today."
            }
        }

        // "Show unusual KM records."
        if (q.contains("unusual") || q.contains("km error") || q.contains("km record")) {
            val unusual = vehicles.filter { it.closingKm != null && ((it.closingKm!! - it.openingKm) > 300 || it.closingKm!! < it.openingKm) }
            return@withContext if (unusual.isNotEmpty()) {
                val list = unusual.joinToString("\n") {
                    val diff = (it.closingKm ?: 0) - it.openingKm
                    "• ${it.vehicleNumber}: Opening ${it.openingKm} KM, Closing ${it.closingKm} KM (Diff: ${diff} KM) [${if (it.supervisorOverride) "Supervisor Override Approved" else "Flagged"}]"
                }
                "Unusual KM Records (${unusual.size}):\n$list"
            } else {
                "No unusual KM records found. All vehicle odometers are within normal operating parameters."
            }
        }

        // "Give today's summary." or "summary"
        if (q.contains("summary") || q.contains("overview") || q.contains("stats")) {
            val vInToday = vehicles.count { it.inTime >= todayStart }
            val vOutToday = vehicles.count { it.outTime != null && it.outTime!! >= todayStart }
            val visInToday = visitors.count { it.inTime >= todayStart }
            val visOutToday = visitors.count { it.outTime != null && it.outTime!! >= todayStart }
            val matInToday = movements.count { it.movementType == "IN" && it.created_at >= todayStart }
            val matOutToday = movements.count { it.movementType == "OUT" && it.created_at >= todayStart }
            val insideV = vehicles.count { it.status == "Inside" }
            val insideVis = visitors.count { it.status == "Inside" }
            val underRep = repairs.count { (it.status == "PENDING" || it.status == "PARTIALLY_RETURNED") && (it.sentQuantity - it.returnedQuantity) > 0 }

            val totalKm = vehicles.filter { it.closingKm != null && it.outTime != null && it.outTime!! >= todayStart }
                .sumOf { (it.closingKm ?: 0) - it.openingKm }

            return@withContext """
                Gate Operations Summary (Today):
                • Vehicles: $vInToday IN / $vOutToday OUT (Currently Inside: $insideV)
                • Visitors: $visInToday IN / $visOutToday OUT (Currently Inside: $insideVis)
                • Materials: $matInToday IN / $matOutToday OUT (Under Repair: $underRep)
                • Total Kilometers Logged: $totalKm KM
                • Active Alerts: ${alerts.size}
            """.trimIndent()
        }

        // Generic query fallback to Gemini if API key is present
        val devKey = BuildConfig.GEMINI_API_KEY
        if (devKey.isNotEmpty() && !devKey.contains("MY_GEMINI_API_KEY")) {
            try {
                return@withContext queryGeminiWithContext(query, devKey, vehicles, visitors, alerts, movements, repairs)
            } catch (_: Exception) {
                // Fallback gracefully
            }
        }

        return@withContext "I couldn't find that information in the available records."
    }

    private suspend fun queryGeminiWithContext(
        userQuery: String,
        apiKey: String,
        vehicles: List<VehicleEntry>,
        visitors: List<VisitorEntry>,
        alerts: List<Alert>,
        movements: List<MaterialMovement>,
        repairs: List<RepairRecord>
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val contextData = """
            Available Gate Data:
            - Vehicles: ${vehicles.size} (Inside: ${vehicles.count { it.status == "Inside" }})
            - Visitors: ${visitors.size} (Inside: ${visitors.count { it.status == "Inside" }})
            - Materials IN: ${movements.count { it.movementType == "IN" }}, Materials OUT: ${movements.count { it.movementType == "OUT" }}
            - Pending Repairs: ${repairs.filter { it.status == "PENDING" }.map { "${it.repairId} (${it.materialDescription} with ${it.repairVendor})" }}
            - Alerts: ${alerts.map { "${it.type}: ${it.message}" }.joinToString("; ")}
        """.trimIndent()

        val systemPrompt = "You are GateAI Assistant for an enterprise logistics gate. Only use the provided Gate Data. Never invent or assume facts. If information is not in the data, strictly reply: 'I couldn't find that information in the available records.' Keep answers under 3 sentences."

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\n\n$contextData\n\nUser Question: $userQuery")
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            return@withContext "I couldn't find that information in the available records."
        }

        val json = JSONObject(respBody)
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
        val text = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")

        text?.trim() ?: "I couldn't find that information in the available records."
    }

    private fun formatDuration(millis: Long): String {
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
