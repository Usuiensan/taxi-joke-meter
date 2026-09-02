package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleDao
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleEntity

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class, DrivingSampleEntity::class], version = 3, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun drivingSampleDao(): DrivingSampleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeFrom TEXT")
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeTo TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS driving_samples (timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL NOT NULL, bearingDegrees REAL, roadMode TEXT NOT NULL, occupancyState TEXT NOT NULL, measurementQuality TEXT NOT NULL, PRIMARY KEY(timestamp))")
            }
        }
    }
}
