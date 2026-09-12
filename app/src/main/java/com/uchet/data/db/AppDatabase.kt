package com.uchet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uchet.data.db.dao.BhaComponentDao
import com.uchet.data.db.dao.CrossoverDao
import com.uchet.data.db.dao.DiameterPresetDao
import com.uchet.data.db.dao.PipeDao
import com.uchet.data.db.dao.RunDao
import com.uchet.data.db.dao.SpoDao
import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.DiameterPreset
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.model.SpoEntity

@Database(
    entities = [
        SpoEntity::class,
        RunEntity::class,
        PipeEntity::class,
        CrossoverEntity::class,
        BhaComponentEntity::class,
        DiameterPreset::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun spoDao(): SpoDao
    abstract fun runDao(): RunDao
    abstract fun pipeDao(): PipeDao
    abstract fun crossoverDao(): CrossoverDao
    abstract fun bhaComponentDao(): BhaComponentDao
    abstract fun diameterPresetDao(): DiameterPresetDao

    companion object {
        const val DB_NAME = "uchet.db"

        val DEFAULT_PRESETS = listOf(
            "НКТ 60", "НКТ 73", "НКТ 89", "НКТ 102", "НКТ 114",
            "СБТ 2 3/8", "СБТ 2 7/8"
        )

        /**
         * Приёмка п.7: версия поднята с 7 до 8, добавлено тестовое поле,
         * написана настоящая [Migration] (данные сохраняются при обновлении).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spo ADD COLUMN testField TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_7_8)

        /**
         * Дедупликация и вставка дефолтных пресетов выполняется в [Callback.onOpen]
         * (а не в onCreate), как того требует ТЗ: сначала DELETE по MIN(id) в GROUP BY name,
         * затем INSERT ... WHERE NOT EXISTS — самовосстановление при каждом открытии БД.
         */
        private val seedCallback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL(
                    "DELETE FROM diameter_presets WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM diameter_presets GROUP BY name)"
                )
                for (name in DEFAULT_PRESETS) {
                    db.execSQL(
                        "INSERT INTO diameter_presets (name) SELECT ? " +
                            "WHERE NOT EXISTS (SELECT 1 FROM diameter_presets WHERE name = ?)",
                        arrayOf(name, name)
                    )
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                // Старые версии (1..6) без ценных данных можно стереть. Безусловный
                // fallbackToDestructiveMigration() НЕ используется: начиная с версии 7
                // любое изменение схемы обязано сопровождаться Migration, иначе падение.
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                .addCallback(seedCallback)
                .build()
    }
}
