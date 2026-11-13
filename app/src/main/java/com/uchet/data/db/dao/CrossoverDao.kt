package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.uchet.data.model.CrossoverEntity

@Dao
interface CrossoverDao {

    @Insert
    suspend fun insert(crossover: CrossoverEntity): Long

    @Update
    suspend fun update(crossover: CrossoverEntity)

    @Query("DELETE FROM crossovers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM crossovers WHERE runId = :runId")
    suspend fun deleteAllInRun(runId: Long)

    @Query("SELECT * FROM crossovers WHERE runId = :runId ORDER BY afterPipeIndex ASC, id ASC")
    suspend fun getCrossoversForRun(runId: Long): List<CrossoverEntity>

    @Query("SELECT * FROM crossovers WHERE runId = :runId ORDER BY afterPipeIndex ASC, id ASC")
    fun observeCrossoversForRun(runId: Long): kotlinx.coroutines.flow.Flow<List<CrossoverEntity>>

    /**
     * Shift [afterPipeIndex] of crossovers anchored after [afterIndex].
     * Also used when deleting the pipe at [afterIndex]: crossovers anchored
     * exactly at that pipe move down to the previous pipe.
     */
    @Query(
        "UPDATE crossovers SET afterPipeIndex = afterPipeIndex + :delta " +
            "WHERE runId = :runId AND afterPipeIndex > :afterIndex"
    )
    suspend fun shiftIndicesAfter(runId: Long, afterIndex: Int, delta: Int)

    @Query("SELECT * FROM crossovers WHERE runId = :runId AND afterPipeIndex = :afterPipeIndex ORDER BY id ASC")
    suspend fun getAnchoredAt(runId: Long, afterPipeIndex: Int): List<CrossoverEntity>

    @Query(
        """
        SELECT * FROM crossovers
        WHERE runId IN (SELECT id FROM runs WHERE spoId = :spoId)
        ORDER BY runId ASC, afterPipeIndex ASC, id ASC
        """
    )
    suspend fun getCrossoversForSpo(spoId: Long): List<CrossoverEntity>

    @Query(
        """
        SELECT * FROM crossovers
        WHERE runId IN (SELECT id FROM runs WHERE spoId = :spoId)
        ORDER BY runId ASC, afterPipeIndex ASC, id ASC
        """
    )
    fun observeCrossoversForSpo(spoId: Long): kotlinx.coroutines.flow.Flow<List<CrossoverEntity>>
}
