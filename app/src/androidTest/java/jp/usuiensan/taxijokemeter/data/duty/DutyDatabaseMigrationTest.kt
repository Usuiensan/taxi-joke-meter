package jp.usuiensan.taxijokemeter.data.duty

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DutyDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseName = "duty-v8-to-v10-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After fun tearDown() { context.deleteDatabase(databaseName) }

    @Test fun migrationKeepsLegacyRowsAndMapsFareColumns() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        Room.databaseBuilder(context, V8DutyDatabase::class.java, databaseName).build().also { old ->
            old.dao().insertSession(DutySessionEntity("duty-v8", now, null))
            old.dao().insertTrip(V8TripEntity("trip-v8", "duty-v8", now, operatingDistanceMeters = 1_250, fareYen = 1_500, additionalChargeYen = 200))
            old.close()
        }

        val migrated = DutyDatabase.build(context, databaseName)
        val db = migrated.openHelper.readableDatabase
        assertEquals(10, db.version)
        db.query("SELECT actualDistanceMeters, chargeableDistanceMeters, meteredFareYen, totalFareYen FROM trips WHERE id = 'trip-v8'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1_250L, it.getLong(0))
            assertEquals(1_250L, it.getLong(1))
            assertEquals(1_500L, it.getLong(2))
            assertEquals(1_700L, it.getLong(3))
        }
        assertTrue(db.query("SELECT id FROM duty_sessions WHERE id = 'duty-v8'").use { it.moveToFirst() })
        assertTrue(db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'trip_places'").use { it.moveToFirst() })
        db.query("SELECT fareAdjustmentType, fareAdjustmentPercent FROM trips WHERE id = 'trip-v8'").use {
            assertTrue(it.moveToFirst())
            assertEquals("NONE", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        migrated.close()
    }
}

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class V8TripEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pickupAt: Instant,
    val dropoffAt: Instant? = null,
    val pickupLatitude: Double? = null, val pickupLongitude: Double? = null, val pickupAccuracyMeters: Double? = null, val pickupRecordedAt: Instant? = null, val pickupAddressText: String? = null, val pickupLocationPrecision: String? = null, val pickupSource: String? = null,
    val dropoffLatitude: Double? = null, val dropoffLongitude: Double? = null, val dropoffAccuracyMeters: Double? = null, val dropoffRecordedAt: Instant? = null, val dropoffAddressText: String? = null, val dropoffLocationPrecision: String? = null, val dropoffSource: String? = null,
    val passengerCount: Int = 1,
    val operatingDistanceMeters: Long = 0,
    val fareYen: Long = 0,
    val additionalChargeYen: Long = 0,
    val paymentMethod: String = "UNKNOWN",
    val note: String? = null,
    val status: String = "COMPLETED",
    val lowSpeedSeconds: Long = 0,
    val revenueIncluded: Boolean = true,
)

@Dao interface V8DutyDao {
    @Insert suspend fun insertSession(value: DutySessionEntity)
    @Insert suspend fun insertTrip(value: V8TripEntity)
}

@Database(entities = [DutySessionEntity::class, V8TripEntity::class, DutyEventEntity::class, jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleEntity::class, jp.usuiensan.taxijokemeter.data.safety.SafetyEventEntity::class], version = 8, exportSchema = false)
@androidx.room.TypeConverters(DutyConverters::class)
abstract class V8DutyDatabase : RoomDatabase() { abstract fun dao(): V8DutyDao }
