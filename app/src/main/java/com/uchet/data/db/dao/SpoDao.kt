package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.uchet.data.model.SpoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpoDao {

    @Insert
    suspend fun insert(spo: SpoEntity): Long

    @Update
    suspend fun update(spo: SpoEntity)

    @Delete
    suspend fun delete(spo: SpoEntity)

    @Query("SELECT * FROM spo WHERE id = :id")
    suspend fun getById(id: Long): SpoEntity?

    @Query("SELECT * FROM spo ORDER BY startDate DESC, id DESC")
    suspend fun getAll(): List<SpoEntity>

    @Query("SELECT * FROM spo ORDER BY startDate DESC, id DESC")
    fun observeAll(): Flow<List<SpoEntity>>

    @Query("SELECT * FROM spo WHERE id = :id")
    fun observeById(id: Long): Flow<SpoEntity?>

    @Query("SELECT * FROM spo WHERE id = :id")
    suspend fun getByIdSuspend(id: Long): SpoEntity?
}
