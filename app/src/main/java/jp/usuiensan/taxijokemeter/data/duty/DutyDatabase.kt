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
import jp.usuiensan.taxijokemeter.data.safety.SafetyEventDao
import jp.usuiensan.taxijokemeter.data.safety.SafetyEventEntity

@Database(entities = [DutySessionEntity::class, TripEntity::class, TripPlaceEntity::class, TripChargeEntity::class, RestIntervalEntity::class, DutyEventEntity::class, DrivingSampleEntity::class, SafetyEventEntity::class], version = 9, exportSchema = false)
@TypeConverters(DutyConverters::class)
abstract class DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun drivingSampleDao(): DrivingSampleDao
    abstract fun safetyEventDao(): SafetyEventDao

    companion object {
        fun build(context: Context, databaseName: String = "duty.db"): DutyDatabase = Room.databaseBuilder(context, DutyDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE safety_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, startedAtElapsedRealtimeMillis INTEGER NOT NULL, endedAtElapsedRealtimeMillis INTEGER NOT NULL, startedAtEpochMillis INTEGER, endedAtEpochMillis INTEGER, peakValue REAL NOT NULL, roadMode TEXT NOT NULL, source TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_safety_events_startedAtEpochMillis ON safety_events(startedAtEpochMillis)")
                db.execSQL("CREATE INDEX index_safety_events_endedAtEpochMillis ON safety_events(endedAtEpochMillis)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("serviceDate", "TEXT")
                db.addColumnIfMissing("tripNumber", "INTEGER")
                db.addColumnIfMissing("actualDistanceMeters", "INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("chargeableDistanceMeters", "INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("meteredFareYen", "INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("totalFareYen", "INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("fareInitialDistanceMeters", "INTEGER")
                db.addColumnIfMissing("fareInitialFareYen", "INTEGER")
                db.addColumnIfMissing("fareDistanceIncrementMeters", "INTEGER")
                db.addColumnIfMissing("fareDistanceIncrementFareYen", "INTEGER")
                db.addColumnIfMissing("fareLowSpeedLimitKmh", "INTEGER")
                db.addColumnIfMissing("fareLowSpeedTimeIncrementSeconds", "INTEGER")
                db.execSQL("UPDATE trips SET actualDistanceMeters = operatingDistanceMeters, chargeableDistanceMeters = operatingDistanceMeters, meteredFareYen = fareYen, totalFareYen = fareYen + additionalChargeYen")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trips_serviceDate_tripNumber ON trips(serviceDate, tripNumber)")
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_places (id TEXT NOT NULL, tripId TEXT NOT NULL, type TEXT NOT NULL, sequence INTEGER NOT NULL, recordedAt INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL, canonicalAddress TEXT, reportAddress TEXT, landmarkName TEXT, landmarkDetail TEXT, locationPrecision TEXT, source TEXT, PRIMARY KEY(id), FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_places_tripId ON trip_places(tripId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trip_places_tripId_type_sequence ON trip_places(tripId, type, sequence)")
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_charges (id TEXT NOT NULL, tripId TEXT NOT NULL, type TEXT NOT NULL, amountYen INTEGER NOT NULL, appliedAt INTEGER NOT NULL, label TEXT, PRIMARY KEY(id), FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_charges_tripId ON trip_charges(tripId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS rest_intervals (id TEXT NOT NULL, sessionId TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, startLatitude REAL, startLongitude REAL, startAccuracyMeters REAL, distanceMeters INTEGER NOT NULL, endReason TEXT, PRIMARY KEY(id), FOREIGN KEY(sessionId) REFERENCES duty_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rest_intervals_sessionId ON rest_intervals(sessionId)")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean = query("PRAGMA table_info($table)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(index) else null }.any { it == column }
        }

        private fun SupportSQLiteDatabase.addColumnIfMissing(column: String, definition: String) {
            if (!hasColumn("trips", column)) execSQL("ALTER TABLE trips ADD COLUMN $column $definition")
        }
    }
}
