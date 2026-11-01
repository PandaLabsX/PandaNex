package com.example.data.local.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PandaDao {
    // Alert & Notification Center
    @Query("SELECT * FROM alert_notifications ORDER BY timestamp DESC LIMIT 250")
    fun observeNotifications(): Flow<List<AlertNotificationEntity>>

    @Query("SELECT COUNT(*) FROM alert_notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(entity: AlertNotificationEntity): Long

    @Query("UPDATE alert_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Long)

    @Query("UPDATE alert_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM alert_notifications")
    suspend fun clearAllNotifications()

    // Metric History (CPU, RAM, BATTERY, THERMAL)
    @Query("SELECT * FROM metric_history WHERE metricType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun observeMetricHistory(type: String, limit: Int = 60): Flow<List<MetricHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetricSnapshot(entity: MetricHistoryEntity)

    @Query("DELETE FROM metric_history WHERE timestamp < :olderThanMs")
    suspend fun pruneOldMetrics(olderThanMs: Long)

    @Query("DELETE FROM metric_history")
    suspend fun clearAllMetrics()

    // Network Test History
    @Query("SELECT * FROM network_test_history ORDER BY timestamp DESC LIMIT 50")
    fun observeNetworkTests(): Flow<List<NetworkTestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkTest(entity: NetworkTestEntity)

    @Query("DELETE FROM network_test_history")
    suspend fun clearNetworkTests()

    // Smart Automation Rules (WHEN -> IF -> THEN)
    @Query("SELECT * FROM automation_rules ORDER BY id DESC")
    fun observeAutomationRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getEnabledAutomationRules(): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationRule(rule: AutomationRuleEntity): Long

    @Update
    suspend fun updateAutomationRule(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteAutomationRule(id: Long)

    @Query("SELECT COUNT(*) FROM automation_rules")
    suspend fun getAutomationRuleCount(): Int

    // Smart Automation Execution History
    @Query("SELECT * FROM automation_events ORDER BY timestamp DESC LIMIT 100")
    fun observeAutomationEvents(): Flow<List<AutomationEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomationEvent(event: AutomationEventEntity)

    @Query("DELETE FROM automation_events")
    suspend fun clearAutomationEvents()

    // Connection Monitor Events
    @Query("SELECT * FROM connection_events ORDER BY timestamp DESC LIMIT 100")
    fun observeConnectionEvents(): Flow<List<ConnectionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectionEvent(event: ConnectionEventEntity)

    @Query("DELETE FROM connection_events")
    suspend fun clearConnectionEvents()
}

@Database(
    entities = [
        AlertNotificationEntity::class,
        MetricHistoryEntity::class,
        NetworkTestEntity::class,
        AutomationRuleEntity::class,
        AutomationEventEntity::class,
        ConnectionEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PandaDatabase : RoomDatabase() {
    abstract fun pandaDao(): PandaDao

    companion object {
        @Volatile
        private var INSTANCE: PandaDatabase? = null

        fun getInstance(context: Context): PandaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PandaDatabase::class.java,
                    "pandanex_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
