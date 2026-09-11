package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uchet.data.model.BhaComponentEntity

@Dao
interface BhaComponentDao {

    @Insert
    suspend fun insert(component: BhaComponentEntity): Long

    @Query("DELETE FROM bha_components WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bha_components WHERE spoId = :spoId")
    suspend fun deleteAllInSpo(spoId: Long)

    @Query("SELECT * FROM bha_components WHERE spoId = :spoId ORDER BY indexInBha ASC")
    suspend fun getForSpo(spoId: Long): List<BhaComponentEntity>

    @Query("SELECT * FROM bha_components WHERE spoId = :spoId ORDER BY indexInBha ASC")
    fun observeForSpo(spoId: Long): kotlinx.coroutines.flow.Flow<List<BhaComponentEntity>>

    /** Total BHA length for the SPO (0 if empty). */
    @Query("SELECT COALESCE(SUM(lengthM), 0.0) FROM bha_components WHERE spoId = :spoId")
    suspend fun getBhaTotal(spoId: Long): Double

    @Query("SELECT COALESCE(MAX(indexInBha), -1) FROM bha_components WHERE spoId = :spoId")
    suspend fun getMaxIndex(spoId: Long): Int
}
