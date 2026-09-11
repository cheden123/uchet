package com.uchet.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diameter_presets")
data class DiameterPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
