package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.uchet.data.model.PipeEntity

@Dao
interface PipeDao {

    @Insert
    suspend fun insert(pipe: PipeEntity): Long

    @Update
    suspend fun update(pipe: PipeEntity)

    @Query("DELETE FROM pipes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pipes WHERE runId = :runId")
    suspend fun deleteAllInRun(runId: Long)

    @Query("SELECT * FROM pipes WHERE runId = :runId ORDER BY indexInRun ASC")
    suspend fun getPipesForRun(runId: Long): List<PipeEntity>

    @Query("SELECT * FROM pipes WHERE runId = :runId ORDER BY indexInRun ASC")
    fun observePipesForRun(runId: Long): kotlinx.coroutines.flow.Flow<List<PipeEntity>>

    @Query(
        """
        SELECT * FROM pipes
        WHERE runId IN (SELECT id FROM runs WHERE spoId = :spoId)
        ORDER BY runId ASC, indexInRun ASC
        """
    )
    suspend fun getPipesForSpo(spoId: Long): List<PipeEntity>

    /** Shifts pipes with [indexInRun] greater than [afterIndex] by [delta] (used for insertion/deletion). */
    @Query("UPDATE pipes SET indexInRun = indexInRun + :delta WHERE runId = :runId AND indexInRun > :afterIndex")
    suspend fun shiftIndicesAfter(runId: Long, afterIndex: Int, delta: Int)

    /** Number of pipes that exist at or above [indexInRun] in the run — for deletion bookkeeping. */
    @Query("SELECT COUNT(*) FROM pipes WHERE runId = :runId AND indexInRun >= :indexInRun")
    suspend fun countFrom(runId: Long, indexInRun: Int): Int

    @Query("SELECT COUNT(*) FROM pipes WHERE runId = :runId")
    suspend fun countInRun(runId: Long): Int

    @Query(
        """
        SELECT * FROM pipes
        WHERE runId IN (SELECT id FROM runs WHERE spoId = :spoId)
        ORDER BY runId ASC, indexInRun ASC
        """
    )
    fun observePipesForSpo(spoId: Long): kotlinx.coroutines.flow.Flow<List<PipeEntity>>
}
