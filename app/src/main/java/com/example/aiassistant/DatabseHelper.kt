package com.example.aiassistant

import android.content.Context
import androidx.room.*

// --- Entities ---

@Entity
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val state: String,
    val actionType: String,
    val actionDetail: String,
    val success: Boolean
)

@Entity
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyword: String,
    val actionType: String = "CLICK",
    val enabled: Boolean = true
)

@Entity(
    indices = [Index(value = ["state", "actionText"], unique = true)]
)
data class UserPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val state: String,
    val packageName: String,
    val actionText: String,
    val actionType: String,
    val count: Int = 1,
    val lastSeen: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM LogEntity ORDER BY timestamp DESC")
    suspend fun getAll(): List<LogEntity>

    @Query("SELECT * FROM LogEntity ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LogEntity>

    @Query("DELETE FROM LogEntity")
    suspend fun clearAll()
}

@Dao
interface RuleDao {
    @Insert
    suspend fun insert(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("SELECT * FROM RuleEntity")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM RuleEntity WHERE enabled = 1")
    suspend fun getEnabled(): List<RuleEntity>

    @Query("SELECT COUNT(*) FROM RuleEntity")
    suspend fun count(): Int
}

@Dao
interface UserPatternDao {
    @Query("SELECT * FROM UserPatternEntity WHERE state = :state ORDER BY count DESC")
    suspend fun getByState(state: String): List<UserPatternEntity>

    @Query("SELECT * FROM UserPatternEntity WHERE state = :state ORDER BY count DESC LIMIT 1")
    suspend fun getTopByState(state: String): UserPatternEntity?

    @Query("SELECT * FROM UserPatternEntity WHERE state = :state AND actionText = :actionText LIMIT 1")
    suspend fun get(state: String, actionText: String): UserPatternEntity?

    @Insert
    suspend fun insert(pattern: UserPatternEntity)

    @Query("UPDATE UserPatternEntity SET count = count + 1, lastSeen = :now WHERE id = :id")
    suspend fun incrementCount(id: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM UserPatternEntity")
    suspend fun totalPatterns(): Int

    @Query("SELECT COUNT(DISTINCT packageName) FROM UserPatternEntity")
    suspend fun distinctApps(): Int

    @Query("SELECT * FROM UserPatternEntity ORDER BY packageName ASC, count DESC")
    suspend fun getAll(): List<UserPatternEntity>

    @Delete
    suspend fun delete(pattern: UserPatternEntity)

    @Query("DELETE FROM UserPatternEntity")
    suspend fun deleteAll()
}

// --- Database ---

@Database(
    entities = [LogEntity::class, RuleEntity::class, UserPatternEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun ruleDao(): RuleDao
    abstract fun userPatternDao(): UserPatternDao
}

// --- Helper ---

object DatabaseHelper {

    private var db: AppDatabase? = null

    fun getDB(context: Context): AppDatabase {
        return db ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ai_db"
        ).fallbackToDestructiveMigration().build().also { db = it }
    }

    suspend fun logAction(
        context: Context,
        packageName: String,
        state: String,
        actionType: String,
        actionDetail: String,
        success: Boolean
    ) {
        getDB(context).logDao().insert(
            LogEntity(
                packageName = packageName,
                state = state,
                actionType = actionType,
                actionDetail = actionDetail,
                success = success
            )
        )
    }

    /**
     * Record or increment a user behavior pattern.
     */
    suspend fun recordPattern(
        context: Context,
        state: String,
        packageName: String,
        actionText: String,
        actionType: String
    ) {
        val dao = getDB(context).userPatternDao()
        val existing = dao.get(state, actionText)
        if (existing != null) {
            dao.incrementCount(existing.id)
        } else {
            dao.insert(
                UserPatternEntity(
                    state = state,
                    packageName = packageName,
                    actionText = actionText,
                    actionType = actionType
                )
            )
        }
    }

    suspend fun seedDefaultRules(context: Context) {
        val dao = getDB(context).ruleDao()
        if (dao.count() == 0) {
            dao.insert(RuleEntity(keyword = "skip", actionType = "CLICK"))
            dao.insert(RuleEntity(keyword = "allow", actionType = "CLICK"))
            dao.insert(RuleEntity(keyword = "ok", actionType = "CLICK"))
            dao.insert(RuleEntity(keyword = "accept", actionType = "CLICK"))
            dao.insert(RuleEntity(keyword = "continue", actionType = "CLICK"))
            dao.insert(RuleEntity(keyword = "dismiss", actionType = "CLICK"))
        }
    }
}
