package com.uchet.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "crossovers",
    foreignKeys = [ForeignKey(RunEntity::class, ["id"], ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("runId")]
)
data class CrossoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val afterPipeIndex: Int,      // 0 = перед первой трубой ряда
    val type: Int = 0,            // 0=Переводник, 1=Шаблон, 2=Свое
    val customTypeName: String = "",
    val diameterLabel: String = "",
    val lengthM: Double,
    val note: String = ""
)
