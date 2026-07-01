package com.example.multibarcode.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A write made while offline, queued locally until the user presses "upload".
 * [type] is also the Firestore collection name (orders/customers/payments/products);
 * [payload] is the document as JSON.
 */
@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val label: String,
    val payload: String,
    val createdAt: Long,
)

@Dao
interface PendingOpDao {
    @Insert
    suspend fun insert(op: PendingOp): Long

    @Delete
    suspend fun delete(op: PendingOp)

    @Query("SELECT * FROM pending_ops ORDER BY createdAt")
    fun observeAll(): Flow<List<PendingOp>>

    @Query("SELECT * FROM pending_ops ORDER BY createdAt")
    suspend fun getAll(): List<PendingOp>

    @Query("SELECT COUNT(*) FROM pending_ops")
    fun count(): Flow<Int>
}

@Database(entities = [PendingOp::class], version = 1, exportSchema = false)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun pendingOpDao(): PendingOpDao

    companion object {
        @Volatile
        private var INSTANCE: OutboxDatabase? = null

        fun get(context: Context): OutboxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OutboxDatabase::class.java,
                    "outbox.db",
                ).build().also { INSTANCE = it }
            }
    }
}
