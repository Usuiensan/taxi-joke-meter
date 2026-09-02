package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class], version = 1, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
}
