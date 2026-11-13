package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uchet.data.model.RunEntity

@Dao
interface RunDao {

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("UPDATE runs SET isCompleted = 1 WHERE id = :runId")
    suspend fun completeRun(runId: Long)

    @Query("SELECT * FROM runs WHERE spoId = :spoId ORDER BY createdAt ASC, id ASC")
    suspend fun getRunsForSpo(spoId: Long): List<RunEntity>

    @Query("SELECT * FROM runs WHERE spoId = :spoId AND isCompleted = 0 ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun getActiveRunInSpo(spoId: Long): RunEntity?

    /** Alias used by the repository's mutex-protected ensureActiveRun. */
    @Query("SELECT * FROM runs WHERE spoId = :spoId AND isCompleted = 0 ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun getActiveRunInSpoSuspend(spoId: Long): RunEntity?

    @Query("SELECT * FROM runs WHERE spoId = :spoId AND isCompleted = 0 ORDER BY createdAt ASC, id ASC LIMIT 1")
    fun observeActiveRunInSpo(spoId: Long): kotlinx.coroutines.flow.Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :runId")
    suspend fun getRunById(runId: Long): RunEntity?

    @Query("SELECT * FROM runs WHERE id = :runId")
    fun observeRunById(runId: Long): kotlinx.coroutines.flow.Flow<RunEntity?>

    @Query("SELECT COUNT(*) FROM runs WHERE spoId = :spoId AND isCompleted = 0")
    suspend fun countActiveInSpo(spoId: Long): Int

    @Query("SELECT COUNT(*) FROM runs WHERE spoId = :spoId")
    suspend fun countInSpo(spoId: Long): Int

    @Query("SELECT COUNT(*) FROM runs WHERE spoId = :spoId AND isCompleted = 1")
    suspend fun countCompletedInSpo(spoId: Long): Int

    @Query("SELECT * FROM runs WHERE spoId = :spoId ORDER BY createdAt ASC, id ASC")
    fun observeRunsForSpo(spoId: Long): kotlinx.coroutines.flow.Flow<List<RunEntity>>
}
