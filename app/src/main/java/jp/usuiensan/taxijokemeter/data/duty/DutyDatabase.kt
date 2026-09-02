package jp.usuiensan.taxijokemeter.data.duty

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleDao
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleEntity

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class, DrivingSampleEntity::class], version = 4, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun drivingSampleDao(): DrivingSampleDao

    companion object {
        fun build(context: Context, databaseName: String = "duty.db"): DutyDatabase = Room.databaseBuilder(context, DutyDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeFrom TEXT")
                db.execSQL("ALTER TABLE duty_events ADD COLUMN safetyRoadModeTo TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS driving_samples (timestampEpochSecond INTEGER NOT NULL, timestampNano INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL NOT NULL, bearingDegrees REAL, roadMode TEXT NOT NULL, occupancyState TEXT NOT NULL, measurementQuality TEXT NOT NULL, PRIMARY KEY(timestampEpochSecond, timestampNano))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE driving_samples_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampEpochSecond INTEGER NOT NULL, timestampNano INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL NOT NULL, bearingDegrees REAL, roadMode TEXT NOT NULL, occupancyState TEXT NOT NULL, measurementQuality TEXT NOT NULL)")
                db.execSQL("INSERT INTO driving_samples_new (timestampEpochSecond, timestampNano, latitude, longitude, accuracyMeters, speedMps, bearingDegrees, roadMode, occupancyState, measurementQuality) SELECT timestampEpochSecond, timestampNano, latitude, longitude, accuracyMeters, speedMps, bearingDegrees, roadMode, occupancyState, measurementQuality FROM driving_samples")
                db.execSQL("DROP TABLE driving_samples")
                db.execSQL("ALTER TABLE driving_samples_new RENAME TO driving_samples")
                db.execSQL("CREATE INDEX index_driving_samples_timestampEpochSecond_timestampNano ON driving_samples(timestampEpochSecond, timestampNano)")
            }
        }
    }
}
