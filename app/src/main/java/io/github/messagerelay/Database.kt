package io.github.messagerelay

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class DeliveryRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String = "", val app: String, val title: String, val body: String = "", val status: String, val channelResults: String = "[]", val createdAt: Long, val delayed: Boolean = false)
@Entity data class QueuedMessage(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val app: String, val title: String, val body: String, val createdAt: Long, val scheduledAt: Long = createdAt)
@Entity data class RuleEntity(@PrimaryKey val packageName: String, val appName: String, val includes: String = "", val excludes: String = "", val enabled: Boolean = true, val templateId: String = TemplateCatalog.GENERAL_ID)
@Entity data class TemplateEntity(@PrimaryKey val id: String, val name: String, val title: String, val body: String, val builtIn: Boolean = false)

@Dao interface RelayDao {
    @Query("SELECT * FROM DeliveryRecord ORDER BY createdAt DESC LIMIT 100") fun records(): Flow<List<DeliveryRecord>>
    @Query("SELECT COUNT(*) FROM DeliveryRecord WHERE createdAt >= :since") fun recordCountSince(since: Long): Flow<Int>
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
    @Query("SELECT * FROM RuleEntity WHERE packageName = :packageName AND enabled = 1 LIMIT 1") suspend fun rule(packageName: String): RuleEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRule(rule: RuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRules(rules: List<RuleEntity>)
    @Query("DELETE FROM RuleEntity WHERE packageName = :packageName") suspend fun deleteRule(packageName: String)
    @Query("SELECT * FROM TemplateEntity ORDER BY builtIn DESC, name") fun templatesFlow(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM TemplateEntity ORDER BY builtIn DESC, name") suspend fun allTemplates(): List<TemplateEntity>
    @Query("SELECT * FROM TemplateEntity WHERE id = :id LIMIT 1") suspend fun template(id: String): TemplateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTemplate(template: TemplateEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveTemplates(templates: List<TemplateEntity>)
    @Query("UPDATE RuleEntity SET templateId = :fallbackId WHERE templateId = :templateId") suspend fun fallbackTemplate(templateId: String, fallbackId: String = TemplateCatalog.GENERAL_ID)
    @Query("DELETE FROM TemplateEntity WHERE id = :id AND builtIn = 0") suspend fun deleteCustomTemplate(id: String)
}

@Database(entities = [DeliveryRecord::class, QueuedMessage::class, RuleEntity::class, TemplateEntity::class], version = 3, exportSchema = false)
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
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE RuleEntity ADD COLUMN templateId TEXT NOT NULL DEFAULT 'legacy_global'")
                db.execSQL("CREATE TABLE IF NOT EXISTS TemplateEntity (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `builtIn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        @Volatile private var instance: RelayDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, RelayDatabase::class.java, "message-relay.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { database ->
                instance = database
            }
        }
    }
}

suspend fun RelayDao.ensureTemplates(settings: AppSettings? = null) {
    saveTemplates(TemplateCatalog.builtIns.map { TemplateEntity(it.id, it.name, it.title, it.body, true) })
    if (allRules().any { it.templateId == "legacy_global" }) {
        val title = settings?.templateTitle ?: "[{{app}}] {{title}}"
        val body = settings?.templateBody ?: "{{body}}\n{{time}}"
        saveTemplate(TemplateEntity("legacy_global", "升级前全局模板", title, body))
    }
}

fun TemplateEntity.definition() = TemplateDefinition(id, name, title, body, builtIn)
