package com.uchet.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.uchet.data.model.DiameterPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface DiameterPresetDao {

    @Insert
    suspend fun insert(preset: DiameterPreset): Long

    @Delete
    suspend fun delete(preset: DiameterPreset)

    /** Insertion order: autoincrement id, NOT alphabetical. */
    @Query("SELECT * FROM diameter_presets ORDER BY id ASC")
    suspend fun getAll(): List<DiameterPreset>

    @Query("SELECT * FROM diameter_presets ORDER BY id ASC")
    fun observeAll(): Flow<List<DiameterPreset>>

    @Query("SELECT COUNT(*) FROM diameter_presets WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("SELECT * FROM diameter_presets WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): DiameterPreset?

    /** Keep only the lowest id per duplicate name (table has no UNIQUE constraint). */
    @Query("DELETE FROM diameter_presets WHERE id NOT IN (SELECT MIN(id) FROM diameter_presets GROUP BY name)")
    suspend fun dedupe(): Int
}
