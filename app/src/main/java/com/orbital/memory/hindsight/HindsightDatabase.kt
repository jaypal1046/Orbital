package com.orbital.memory.hindsight

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class HindsightTypeConverters {
    @TypeConverter
    fun fromMemoryType(type: MemoryType): String = type.name

    @TypeConverter
    fun toMemoryType(value: String): MemoryType = runCatching {
        MemoryType.valueOf(value)
    }.getOrDefault(MemoryType.HABIT)
}

@Database(
    entities = [MemoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(HindsightTypeConverters::class)
abstract class HindsightDatabase : RoomDatabase() {
    abstract fun hindsightDao(): HindsightDao
}
