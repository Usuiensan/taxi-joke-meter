package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class], version = 2, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeFrom TEXT")
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeTo TEXT")
            }
        }
    }
}
