package com.uchet.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bha_components",
    foreignKeys = [ForeignKey(SpoEntity::class, ["id"], ["spoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spoId")]
)
data class BhaComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spoId: Long,
    val indexInBha: Int,
    val name: String,
    val lengthM: Double,
    val sizeLabel: String = ""
)
