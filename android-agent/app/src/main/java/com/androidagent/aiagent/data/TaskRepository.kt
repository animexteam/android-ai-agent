package com.androidagent.aiagent.data

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Keep
@Entity(tableName = "tasks")
data class TaskRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goal: String,
    val status: String,
    val startTime: Long,
    val endTime: Long? = null,
    val stepCount: Int = 0,
    val result: String? = null,
    val eventsSummary: String? = null
)

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskRecord): Long

    @Update
    suspend fun update(task: TaskRecord)

    @Query("SELECT * FROM tasks ORDER BY startTime DESC")
    fun getAll(): Flow<List<TaskRecord>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getById(id: Long): Flow<TaskRecord?>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM tasks")
    fun count(): Flow<Int>
}

@Database(entities = [TaskRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun provide(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "android_agent_db"
            ).build()
        }
    }
}

class TaskRepository(private val db: AppDatabase) {

    private val dao: TaskDao get() = db.taskDao()

    suspend fun insertTask(record: TaskRecord): Long {
        return dao.insert(record)
    }

    suspend fun insertTask(
        goal: String,
        status: String,
        startTime: Long,
        endTime: Long? = null,
        stepCount: Int = 0,
        result: String? = null
    ): Long {
        return dao.insert(TaskRecord(
            goal = goal,
            status = status,
            startTime = startTime,
            endTime = endTime,
            stepCount = stepCount,
            result = result
    ))
    }

    fun getAllTasks(): Flow<List<TaskRecord>> = dao.getAll()

    suspend fun getTaskById(id: Long): TaskRecord? = dao.getById(id).first()

    suspend fun deleteTask(id: Long) = dao.delete(id)

    fun getTaskCount(): Flow<Int> = dao.count()
}