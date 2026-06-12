package io.github.messagerelay

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class DeliveryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String = "",
    val app: String,
    val title: String,
    val body: String = "",
    val status: String,
    val channelResults: String = "[]",
    val createdAt: Long,
    val delayed: Boolean = false
)
@Entity data class QueuedMessage(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val app: String, val title: String, val body: String, val createdAt: Long, val scheduledAt: Long = createdAt)
@Entity data class RuleEntity(@PrimaryKey val packageName: String, val appName: String, val includes: String = "", val excludes: String = "", val enabled: Boolean = true)

@Dao interface RelayDao {
    @Query("SELECT * FROM DeliveryRecord ORDER BY createdAt DESC LIMIT 100") fun records(): Flow<List<DeliveryRecord>>
    @Query("SELECT COUNT(*) FROM DeliveryRecord WHERE createdAt >= :since") fun recordCountSince(since: Long): Flow<Int>
    @Query("SELECT * FROM DeliveryRecord WHERE id = :id") suspend fun record(id: Long): DeliveryRecord?
    @Insert suspend fun addRecord(record: DeliveryRecord)
    @Query("DELETE FROM DeliveryRecord WHERE id NOT IN (SELECT id FROM DeliveryRecord ORDER BY createdAt DESC LIMIT 100)") suspend fun trimRecords()
    @Insert suspend fun queue(message: QueuedMessage)
    @Query("SELECT * FROM QueuedMessage ORDER BY createdAt, id LIMIT 100") suspend fun queued(): List<QueuedMessage>
    @Query("SELECT COUNT(*) FROM QueuedMessage") fun queuedCount(): Flow<Int>
    @Query("DELETE FROM QueuedMessage WHERE id = :id") suspend fun removeQueued(id: Long)
    @Query("DELETE FROM QueuedMessage WHERE id NOT IN (SELECT id FROM QueuedMessage ORDER BY createdAt DESC LIMIT 100)") suspend fun trimQueue()
    @Query("SELECT * FROM RuleEntity ORDER BY appName") fun rulesFlow(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM RuleEntity ORDER BY appName") suspend fun allRules(): List<RuleEntity>
    @Query("SELECT * FROM RuleEntity WHERE enabled = 1") suspend fun rules(): List<RuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRule(rule: RuleEntity)
    @Query("DELETE FROM RuleEntity WHERE packageName = :packageName") suspend fun deleteRule(packageName: String)
}

@Database(entities = [DeliveryRecord::class, QueuedMessage::class, RuleEntity::class], version = 2, exportSchema = false)
abstract class RelayDatabase : RoomDatabase() {
    abstract fun relayDao(): RelayDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE DeliveryRecord ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE DeliveryRecord ADD COLUMN body TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE DeliveryRecord ADD COLUMN channelResults TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE QueuedMessage ADD COLUMN scheduledAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: RelayDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, RelayDatabase::class.java, "message-relay.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
