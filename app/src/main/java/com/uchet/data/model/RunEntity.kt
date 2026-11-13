package com.uchet.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "runs",
    foreignKeys = [ForeignKey(SpoEntity::class, ["id"], ["spoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spoId")]
)
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spoId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)
