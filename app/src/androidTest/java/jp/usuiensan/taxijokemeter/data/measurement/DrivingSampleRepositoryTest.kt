package jp.usuiensan.taxijokemeter.data.measurement

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyConverters
import jp.usuiensan.taxijokemeter.data.duty.DutyDao
import jp.usuiensan.taxijokemeter.data.duty.DutyEventEntity
import jp.usuiensan.taxijokemeter.data.duty.DutySessionEntity
import jp.usuiensan.taxijokemeter.data.duty.TripEntity
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.SpeedSource
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivingSampleRepositoryTest {
    private lateinit var database: DutyDatabase
    private lateinit var repository: DrivingSampleRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DutyDatabase::class.java).build()
        repository = DrivingSampleRepository(database)
        runBlocking { database.dutyDao().insertSession(DutySessionEntity("duty-1", Instant.EPOCH, null)) }
    }

    @After fun tearDown() = database.close()

    @Test fun savesAndLoadsValidAndUnavailableSamples() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        val valid = DrivingSample("duty-1", now, 35.0, 139.0, 5.5, 12.0, 90.0, RoadMode.HIGHWAY2, OccupancyState.OCCUPIED, MeasurementQuality.VALID, 10L, SpeedSource.REPORTED)
        val unavailable = DrivingSample("duty-1", now.plusSeconds(1), 0.0, 0.0, 0.0, null, null, RoadMode.GENERAL, OccupancyState.EMPTY, MeasurementQuality.UNAVAILABLE, 20L, SpeedSource.UNAVAILABLE)

        repository.save(valid)
        repository.save(unavailable)

        val sameMillisecond = valid.copy(timestamp = now.plusNanos(1))
        repository.save(sameMillisecond)
        val sameInstant = valid.copy(latitude = 35.1)
        repository.save(sameInstant)

        assertEquals(listOf(valid, sameInstant, sameMillisecond, unavailable), repository.findAll())
        assertEquals(listOf(valid, sameInstant, sameMillisecond, unavailable), repository.findByDutySessionId("duty-1"))
    }

    @Test fun migratesVersion2DatabaseAndRoundTripsDrivingSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "driving-sample-migration-test.db"
        context.deleteDatabase(databaseName)
        val now = Instant.parse("2026-09-03T00:00:00Z")
        Room.databaseBuilder(context, V2DutyDatabase::class.java, databaseName).build().also { v2Database ->
            v2Database.dutyDao().insertSession(DutySessionEntity("duty-v2", now, null))
            assertEquals(DutySessionEntity("duty-v2", now, null), v2Database.dutyDao().findSession("duty-v2"))
            v2Database.close()
        }

        database = DutyDatabase.build(context, databaseName)
        repository = DrivingSampleRepository(database)
        val sample = DrivingSample(
            "legacy-driving-samples", now.plusNanos(123), 35.0, 139.0, 5.5, 12.0, 90.0,
            RoadMode.HIGHWAY2, OccupancyState.OCCUPIED, MeasurementQuality.VALID,
        )

        repository.save(sample)

        assertEquals(DutySessionEntity("duty-v2", now, null), database.dutyDao().findSession("duty-v2"))
        assertEquals(listOf(sample), repository.findAll())
    }

    @Test fun migrationV5KeepsReportedSpeed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "driving-sample-v5-speed-test.db"
        context.deleteDatabase(databaseName)
        val now = Instant.parse("2026-09-03T00:00:00Z")
        Room.databaseBuilder(context, V5DutyDatabase::class.java, databaseName).build().also { v5Database ->
            v5Database.dutyDao().insertSession(DutySessionEntity("duty-v5", now, null))
            v5Database.sampleDao().insert(V5DrivingSampleEntity(
                dutySessionId = "duty-v5", timestampEpochSecond = now.epochSecond, timestampNano = now.nano,
                latitude = 35.0, longitude = 139.0, accuracyMeters = 5.0, speedMps = 12.5,
                bearingDegrees = null, roadMode = RoadMode.GENERAL.name, occupancyState = OccupancyState.EMPTY.name,
                measurementQuality = MeasurementQuality.VALID.name,
            ))
            v5Database.close()
        }

        database = DutyDatabase.build(context, databaseName)
        repository = DrivingSampleRepository(database)

        val migrated = repository.findByDutySessionId("duty-v5")

        assertEquals(12.5, migrated.single().speedMps!!, 0.0)
        assertEquals(SpeedSource.REPORTED, migrated.single().speedSource)
        assertEquals(null, migrated.single().elapsedRealtimeNanos)
    }
}

@Database(entities = [DutySessionEntity::class, TripEntity::class, jp.usuiensan.taxijokemeter.data.duty.TripPlaceEntity::class, jp.usuiensan.taxijokemeter.data.duty.TripChargeEntity::class, jp.usuiensan.taxijokemeter.data.duty.RestIntervalEntity::class, DutyEventEntity::class], version = 2, exportSchema = false)
@androidx.room.TypeConverters(DutyConverters::class)
abstract class V2DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
}

@Entity(
    tableName = "driving_samples",
    foreignKeys = [ForeignKey(entity = DutySessionEntity::class, parentColumns = ["id"], childColumns = ["dutySessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dutySessionId"), Index(value = ["timestampEpochSecond", "timestampNano"])],
)
data class V5DrivingSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dutySessionId: String,
    val timestampEpochSecond: Long,
    val timestampNano: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val speedMps: Double,
    val bearingDegrees: Double?,
    val roadMode: String,
    val occupancyState: String,
    val measurementQuality: String,
)

@Dao
interface V5DrivingSampleDao {
    @Insert suspend fun insert(sample: V5DrivingSampleEntity)
}

@Database(entities = [DutySessionEntity::class, TripEntity::class, jp.usuiensan.taxijokemeter.data.duty.TripPlaceEntity::class, jp.usuiensan.taxijokemeter.data.duty.TripChargeEntity::class, jp.usuiensan.taxijokemeter.data.duty.RestIntervalEntity::class, DutyEventEntity::class, V5DrivingSampleEntity::class], version = 5, exportSchema = false)
@androidx.room.TypeConverters(DutyConverters::class)
abstract class V5DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
    abstract fun sampleDao(): V5DrivingSampleDao
}
