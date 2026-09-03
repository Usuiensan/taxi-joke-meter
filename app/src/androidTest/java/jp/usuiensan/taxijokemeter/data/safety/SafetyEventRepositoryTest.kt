package jp.usuiensan.taxijokemeter.data.safety

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEvent
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventSource
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyEventRepositoryTest {
    private lateinit var database: DutyDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), DutyDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = database.close()

    @Test fun insertsAndQueriesEventsByEpochRange() = runBlocking {
        val event = SafetyEvent(
            type = SafetyEventType.HARD_DECELERATION,
            startedAtElapsedRealtimeMillis = 1_000L,
            endedAtElapsedRealtimeMillis = 4_000L,
            startedAtEpochMillis = Instant.parse("2026-09-03T00:00:01Z").toEpochMilli(),
            endedAtEpochMillis = Instant.parse("2026-09-03T00:00:04Z").toEpochMilli(),
            peakValue = 3.3,
            roadMode = RoadMode.GENERAL,
            source = SafetyEventSource.GPS,
        )
        val repository = SafetyEventRepository(database)

        repository.insert(event)

        assertEquals(listOf(event.copy(id = 1L)), repository.getEventsBetween(0L, Long.MAX_VALUE))
    }
}
