package com.uchet.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipes",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("runId")]
)
data class PipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val indexInRun: Int,
    val lengthM: Double,
    val diameterLabel: String = ""
)
