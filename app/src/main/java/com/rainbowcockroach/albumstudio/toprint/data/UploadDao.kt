package com.rainbowcockroach.albumstudio.toprint.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {

    @Insert
    suspend fun insert(upload: UploadEntity): Long

    @Query("SELECT * FROM uploads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun getById(id: Long): UploadEntity?

    @Query("UPDATE uploads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: UploadStatus)

    @Query("UPDATE uploads SET status = :status, hash = :hash, errorMsg = :errorMsg WHERE id = :id")
    suspend fun updateResult(id: Long, status: UploadStatus, hash: String?, errorMsg: String?)

    @Query("SELECT COUNT(*) FROM uploads WHERE status IN ('QUEUED', 'UPLOADING')")
    fun observeActiveCount(): Flow<Int>
}
