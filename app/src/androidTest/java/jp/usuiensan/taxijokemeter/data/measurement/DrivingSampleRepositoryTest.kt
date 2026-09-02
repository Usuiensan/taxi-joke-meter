package jp.usuiensan.taxijokemeter.data.measurement

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
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
    }

    @After fun tearDown() = database.close()

    @Test fun savesAndLoadsValidAndUnavailableSamples() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        val valid = DrivingSample(now, 35.0, 139.0, 5.5, 12.0, 90.0, RoadMode.HIGHWAY2, OccupancyState.OCCUPIED, MeasurementQuality.VALID)
        val unavailable = DrivingSample(now.plusSeconds(1), 0.0, 0.0, 0.0, 0.0, null, RoadMode.GENERAL, OccupancyState.EMPTY, MeasurementQuality.UNAVAILABLE)

        repository.save(valid)
        repository.save(unavailable)

        val sameMillisecond = valid.copy(timestamp = now.plusNanos(1))
        repository.save(sameMillisecond)
        val sameInstant = valid.copy(latitude = 35.1)
        repository.save(sameInstant)

        assertEquals(listOf(valid, sameInstant, sameMillisecond, unavailable), repository.findAll())
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
            now.plusNanos(123), 35.0, 139.0, 5.5, 12.0, 90.0,
            RoadMode.HIGHWAY2, OccupancyState.OCCUPIED, MeasurementQuality.VALID,
        )

        repository.save(sample)

        assertEquals(DutySessionEntity("duty-v2", now, null), database.dutyDao().findSession("duty-v2"))
        assertEquals(listOf(sample), repository.findAll())
    }
}

@Database(entities = [DutySessionEntity::class, TripEntity::class, DutyEventEntity::class], version = 2, exportSchema = false)
@androidx.room.TypeConverters(DutyConverters::class)
abstract class V2DutyDatabase : RoomDatabase() {
    abstract fun dutyDao(): DutyDao
}
