package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE syncStatus != 3 ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE syncStatus != 3 ORDER BY timestamp DESC")
    suspend fun getAllSynchronous(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE feishuRecordId = :recordId LIMIT 1")
    suspend fun getByFeishuRecordId(recordId: String): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE syncStatus != 3 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): DiaryEntry?

    @Query("SELECT * FROM diary_entries WHERE syncStatus = 1")
    suspend fun getPendingUpload(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE syncStatus = 2")
    suspend fun getPendingUpdate(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE syncStatus = 3")
    suspend fun getPendingDelete(): List<DiaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntry>)

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        SELECT * FROM diary_entries 
        WHERE syncStatus != 3 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun search(query: String): Flow<List<DiaryEntry>>
}
