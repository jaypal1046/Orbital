package com.orbital.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OrbitalDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId TEXT NOT NULL DEFAULT 'default'")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionTitle TEXT NOT NULL DEFAULT 'Past conversation'")
            }
        }
    }
}
