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

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class, DrivingSampleEntity::class], version = 7, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun drivingSampleDao(): DrivingSampleDao

    companion object {
        fun build(context: Context, databaseName: String = "duty.db"): DutyDatabase = Room.databaseBuilder(context, DutyDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO duty_sessions (id, startedAt, endedAt) VALUES ('legacy-driving-samples', 0, NULL)")
                db.execSQL("CREATE TABLE driving_samples_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dutySessionId TEXT NOT NULL, timestampEpochSecond INTEGER NOT NULL, timestampNano INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL NOT NULL, bearingDegrees REAL, roadMode TEXT NOT NULL, occupancyState TEXT NOT NULL, measurementQuality TEXT NOT NULL, FOREIGN KEY(dutySessionId) REFERENCES duty_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO driving_samples_new (id, dutySessionId, timestampEpochSecond, timestampNano, latitude, longitude, accuracyMeters, speedMps, bearingDegrees, roadMode, occupancyState, measurementQuality) SELECT id, 'legacy-driving-samples', timestampEpochSecond, timestampNano, latitude, longitude, accuracyMeters, speedMps, bearingDegrees, roadMode, occupancyState, measurementQuality FROM driving_samples")
                db.execSQL("DROP TABLE driving_samples")
                db.execSQL("ALTER TABLE driving_samples_new RENAME TO driving_samples")
                db.execSQL("CREATE INDEX index_driving_samples_dutySessionId ON driving_samples(dutySessionId)")
                db.execSQL("CREATE INDEX index_driving_samples_timestampEpochSecond_timestampNano ON driving_samples(timestampEpochSecond, timestampNano)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE driving_samples_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dutySessionId TEXT NOT NULL, timestampEpochSecond INTEGER NOT NULL, timestampNano INTEGER NOT NULL, elapsedRealtimeNanos INTEGER, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, speedMps REAL, speedSource TEXT NOT NULL, bearingDegrees REAL, roadMode TEXT NOT NULL, occupancyState TEXT NOT NULL, measurementQuality TEXT NOT NULL, FOREIGN KEY(dutySessionId) REFERENCES duty_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO driving_samples_new (id, dutySessionId, timestampEpochSecond, timestampNano, elapsedRealtimeNanos, latitude, longitude, accuracyMeters, speedMps, speedSource, bearingDegrees, roadMode, occupancyState, measurementQuality) SELECT id, dutySessionId, timestampEpochSecond, timestampNano, NULL, latitude, longitude, accuracyMeters, speedMps, 'REPORTED', bearingDegrees, roadMode, occupancyState, measurementQuality FROM driving_samples")
                db.execSQL("DROP TABLE driving_samples")
                db.execSQL("ALTER TABLE driving_samples_new RENAME TO driving_samples")
                db.execSQL("CREATE INDEX index_driving_samples_dutySessionId ON driving_samples(dutySessionId)")
                db.execSQL("CREATE INDEX index_driving_samples_timestampEpochSecond_timestampNano ON driving_samples(timestampEpochSecond, timestampNano)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("trips", "status")) db.execSQL("ALTER TABLE trips ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
                if (!db.hasColumn("trips", "lowSpeedSeconds")) db.execSQL("ALTER TABLE trips ADD COLUMN lowSpeedSeconds INTEGER NOT NULL DEFAULT 0")
                if (!db.hasColumn("trips", "revenueIncluded")) db.execSQL("ALTER TABLE trips ADD COLUMN revenueIncluded INTEGER NOT NULL DEFAULT 1")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean = query("PRAGMA table_info($table)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(index) else null }.any { it == column }
        }
    }
}
