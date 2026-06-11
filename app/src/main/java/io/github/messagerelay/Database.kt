package io.github.messagerelay

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class DeliveryRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val app: String, val title: String, val status: String, val createdAt: Long, val delayed: Boolean = false)
@Entity data class QueuedMessage(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val app: String, val title: String, val body: String, val createdAt: Long)
@Entity data class RuleEntity(@PrimaryKey val packageName: String, val appName: String, val includes: String = "", val excludes: String = "", val enabled: Boolean = true)

@Dao interface RelayDao {
    @Query("SELECT * FROM DeliveryRecord ORDER BY createdAt DESC LIMIT 100") fun records(): Flow<List<DeliveryRecord>>
    @Insert suspend fun addRecord(record: DeliveryRecord)
    @Query("DELETE FROM DeliveryRecord WHERE id NOT IN (SELECT id FROM DeliveryRecord ORDER BY createdAt DESC LIMIT 100)") suspend fun trimRecords()
    @Insert suspend fun queue(message: QueuedMessage)
    @Query("SELECT * FROM QueuedMessage ORDER BY createdAt LIMIT 100") suspend fun queued(): List<QueuedMessage>
    @Query("DELETE FROM QueuedMessage WHERE id = :id") suspend fun removeQueued(id: Long)
    @Query("DELETE FROM QueuedMessage WHERE id NOT IN (SELECT id FROM QueuedMessage ORDER BY createdAt DESC LIMIT 100)") suspend fun trimQueue()
    @Query("SELECT * FROM RuleEntity WHERE enabled = 1") suspend fun rules(): List<RuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRule(rule: RuleEntity)
}

@Database(entities = [DeliveryRecord::class, QueuedMessage::class, RuleEntity::class], version = 1)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun relayDao(): RelayDao
    companion object {
        @Volatile private var instance: RelayDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context, RelayDatabase::class.java, "message-relay.db").build().also { instance = it } }
    }
}
