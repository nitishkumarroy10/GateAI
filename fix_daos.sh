sed -i '/interface VehicleEntryDao {/a \
    @Query("SELECT * FROM vehicle_entries WHERE syncStatus = :status")\n    suspend fun getEntriesBySyncStatus(status: String = "PENDING"): List<VehicleEntry>\n\n    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")\n    suspend fun updateSyncStatus(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt

sed -i '/interface VisitorEntryDao {/a \
    @Query("SELECT * FROM visitor_entries WHERE syncStatus = :status")\n    suspend fun getEntriesBySyncStatus(status: String = "PENDING"): List<VisitorEntry>\n\n    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")\n    suspend fun updateSyncStatus(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt

sed -i '/interface MaterialMovementDao {/a \
    @Query("SELECT * FROM material_movements WHERE syncStatus = :status")\n    suspend fun getMovementsBySyncStatus(status: String = "PENDING"): List<MaterialMovement>\n\n    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")\n    suspend fun updateSyncStatus(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt
