package com.example.pos.modules.database

import android.content.Context
import com.example.pos.data.db.PosDatabase

/**
 * Modular foundation for Database (Module 17).
 * Encapsulates Room database access and configuration.
 */
object DatabaseFeature {
    fun getDatabase(context: Context): PosDatabase {
        return PosDatabase.getInstance(context)
    }
}
