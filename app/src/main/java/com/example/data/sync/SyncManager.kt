package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.GateAiRepository
import com.example.data.local.MaterialMovement
import com.example.data.local.VehicleEntry
import com.example.data.local.VisitorEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

enum class ConnectionStatus {
    ONLINE,
    OFFLINE,
    SYNCING
}

data class SyncInfo(
    val status: ConnectionStatus = ConnectionStatus.ONLINE,
    val pendingRecordsCount: Int = 0,
    val isBackendConnected: Boolean = false,
    val message: String = "Local Pilot Storage Active (Zero Data Loss)"
)

/**
 * Supabase REST API definition
 */
interface SupabaseApi {
    @POST("rest/v1/vehicle_entries")
    suspend fun pushVehicleEntry(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body entry: VehicleEntry
    )

    @POST("rest/v1/visitor_entries")
    suspend fun pushVisitorEntry(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body entry: VisitorEntry
    )
    
    @POST("rest/v1/material_movements")
    suspend fun pushMaterialMovement(
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body entry: MaterialMovement
    )
}

/**
 * Sync Architecture Interface:
 * Android App -> Local Room Database -> Sync Layer -> Cloud Backend API -> PostgreSQL/Supabase
 */
interface CloudSyncGateway {
    suspend fun pushVehicleEntry(entry: VehicleEntry): Result<Unit>
    suspend fun pushVisitorEntry(entry: VisitorEntry): Result<Unit>
    suspend fun pushMaterialMovement(entry: MaterialMovement): Result<Unit>
    suspend fun fetchCloudUpdates(): Result<Unit>
}

class SupabaseCloudSyncGateway : CloudSyncGateway {
    
    // In production, these should be injected securely or fetched from BuildConfig/Env
    // We use fallback dummy strings to prevent crashes if not defined in .env
    private val supabaseUrl = runCatching { BuildConfig::class.java.getField("SUPABASE_URL").get(null) as String }.getOrDefault("https://your-project.supabase.co")
    private val supabaseKey = runCatching { BuildConfig::class.java.getField("SUPABASE_ANON_KEY").get(null) as String }.getOrDefault("your-anon-key")
    
    private val api: SupabaseApi by lazy {
        val interceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        
        Retrofit.Builder()
            .baseUrl(if (supabaseUrl.endsWith("/")) supabaseUrl else "$supabaseUrl/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    override suspend fun pushVehicleEntry(entry: VehicleEntry): Result<Unit> {
        return try {
            api.pushVehicleEntry(apiKey = supabaseKey, auth = "Bearer $supabaseKey", entry = entry)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushVisitorEntry(entry: VisitorEntry): Result<Unit> {
        return try {
            api.pushVisitorEntry(apiKey = supabaseKey, auth = "Bearer $supabaseKey", entry = entry)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pushMaterialMovement(entry: MaterialMovement): Result<Unit> {
        return try {
            api.pushMaterialMovement(apiKey = supabaseKey, auth = "Bearer $supabaseKey", entry = entry)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchCloudUpdates(): Result<Unit> {
        // Implementation for pull sync
        return Result.success(Unit)
    }
}

class SyncManager(context: Context, private val repository: GateAiRepository? = null) {
    private val _syncState = MutableStateFlow(SyncInfo())
    val syncState: StateFlow<SyncInfo> = _syncState.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        
    private val cloudGateway: CloudSyncGateway = SupabaseCloudSyncGateway()
    private val syncScope = CoroutineScope(Dispatchers.IO)

    init {
        checkInitialNetwork()
        registerNetworkCallback()
        startSyncWorker()
    }

    private fun checkInitialNetwork() {
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateStatus(if (isOnline) ConnectionStatus.ONLINE else ConnectionStatus.OFFLINE)
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager?.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateStatus(ConnectionStatus.ONLINE)
                        triggerSync()
                    }

                    override fun onLost(network: Network) {
                        updateStatus(ConnectionStatus.OFFLINE)
                    }
                }
            )
        } catch (_: Exception) {
            // Fallback gracefully in testing / restricted environments
        }
    }

    fun updateStatus(status: ConnectionStatus) {
        val msg = when (status) {
            ConnectionStatus.ONLINE -> "Online — Local Room DB active & cloud-ready"
            ConnectionStatus.OFFLINE -> "Offline — transactions will sync when connection returns"
            ConnectionStatus.SYNCING -> "Syncing transactions with secure gateway..."
        }
        _syncState.value = _syncState.value.copy(
            status = status,
            message = msg
        )
    }

    fun setPendingCount(count: Int) {
        _syncState.value = _syncState.value.copy(pendingRecordsCount = count)
    }
    
    private fun triggerSync() {
        syncScope.launch {
            processPendingQueue()
        }
    }
    
    private fun startSyncWorker() {
        syncScope.launch {
            while (true) {
                if (_syncState.value.status == ConnectionStatus.ONLINE) {
                    processPendingQueue()
                }
                delay(30_000) // Run every 30 seconds
            }
        }
    }
    
    private suspend fun processPendingQueue() {
        if (repository == null) return
        
        try {
            updateStatus(ConnectionStatus.SYNCING)
            
            // 1. Process pending Vehicles
            val pendingVehicles = repository.getPendingVehicleEntries()
            for (vehicle in pendingVehicles) {
                val result = cloudGateway.pushVehicleEntry(vehicle)
                if (result.isSuccess) {
                    repository.updateVehicleSyncStatus(vehicle.entryId, "SYNCED")
                }
            }
            
            // 2. Process pending Visitors
            val pendingVisitors = repository.getPendingVisitorEntries()
            for (visitor in pendingVisitors) {
                val result = cloudGateway.pushVisitorEntry(visitor)
                if (result.isSuccess) {
                    repository.updateVisitorSyncStatus(visitor.entryId, "SYNCED")
                }
            }
            
            // 3. Process pending Material Movements
            val pendingMaterials = repository.getPendingMaterialMovements()
            for (material in pendingMaterials) {
                val result = cloudGateway.pushMaterialMovement(material)
                if (result.isSuccess) {
                    repository.updateMaterialSyncStatus(material.movementId, "SYNCED")
                }
            }
            
            // Update pending count
            val totalPending = repository.getPendingVehicleEntries().size + 
                               repository.getPendingVisitorEntries().size + 
                               repository.getPendingMaterialMovements().size
                               
            setPendingCount(totalPending)
            updateStatus(ConnectionStatus.ONLINE)
            
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed", e)
            updateStatus(ConnectionStatus.ONLINE) // Revert to online but waiting for next tick
        }
    }
}
