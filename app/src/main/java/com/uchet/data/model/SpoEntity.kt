package com.uchet.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spo")
data class SpoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: Long = System.currentTimeMillis(),
    val wellCluster: String = "",
    val wellNumber: String = "",
    val fieldName: String = "",
    val title: String = "",        // "Спуск пера", "Спуск пакера" и т.п.
    val isCompleted: Boolean = false,
    // Тестовое поле (приёмка п.7): добавлено миграцией 7 -> 8.
    @ColumnInfo(defaultValue = "''") val testField: String = ""
)
