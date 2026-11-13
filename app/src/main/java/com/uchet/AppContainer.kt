package com.uchet

import android.content.Context
import android.content.SharedPreferences
import com.uchet.data.db.AppDatabase
import com.uchet.data.repository.UchetRepository

/** Ручной service-locator (без Hilt) — достаточно для одного приложения. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("uchet_prefs", Context.MODE_PRIVATE)
    }

    val repository: UchetRepository by lazy { UchetRepository(database, prefs) }
}
