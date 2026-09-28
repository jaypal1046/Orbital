package com.orbital.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OrbitalDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
