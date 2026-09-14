sed -i '/suspend fun updateSyncStatus/i \    @Query("UPDATE vehicle_entries SET syncStatus = :status WHERE entryId IN (:ids)")\n    fun updateSyncStatusBlocking(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt

sed -i '/suspend fun updateSyncStatus/i \    @Query("UPDATE visitor_entries SET syncStatus = :status WHERE entryId IN (:ids)")\n    fun updateSyncStatusBlocking(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt

sed -i '/suspend fun updateSyncStatus/i \    @Query("UPDATE material_movements SET syncStatus = :status WHERE movementId IN (:ids)")\n    fun updateSyncStatusBlocking(ids: List<String>, status: String)' app/src/main/java/com/example/data/local/Daos.kt

sed -i '/interface MaterialMovementDao {/a \
    @Query("SELECT * FROM material_movement_items WHERE movementId IN (:movementIds)")\n    suspend fun getItemsByMovementIds(movementIds: List<String>): List<MaterialMovementItem>' app/src/main/java/com/example/data/local/Daos.kt
