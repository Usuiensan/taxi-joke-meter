package jp.usuiensan.taxijokemeter.data.duty

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import jp.usuiensan.taxijokemeter.domain.model.DutyEvent
import jp.usuiensan.taxijokemeter.domain.model.DutyEventType
import jp.usuiensan.taxijokemeter.domain.model.DutySession
import jp.usuiensan.taxijokemeter.domain.model.LocationPrecision
import jp.usuiensan.taxijokemeter.domain.model.LocationSource
import jp.usuiensan.taxijokemeter.domain.model.PaymentMethod
import jp.usuiensan.taxijokemeter.domain.model.RecordedPlace
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.SafetyRoadModeChange
import jp.usuiensan.taxijokemeter.domain.model.Trip
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DutyRecordRepositoryTest {
    private lateinit var database: DutyDatabase
    private lateinit var repository: DutyRecordRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DutyDatabase::class.java).build()
        repository = DutyRecordRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun savesAndReloadsSessionTripsEventsAndUnresolvedPlace() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        val place = RecordedPlace(35.7295, 139.7168, 12.0, now, null, LocationPrecision.UNKNOWN, LocationSource.GPS)
        val session = DutySession(
            "duty-1", now, now.plusSeconds(3_600),
            trips = listOf(
                Trip("trip-1", now.plusSeconds(60), now.plusSeconds(300), place, null, 2, 1_250, 1_500, 200, PaymentMethod.CASH, "備考"),
                Trip("trip-2", now.plusSeconds(600), now.plusSeconds(900), null, place, 1, 2_000, 2_200, 0, PaymentMethod.CARD, null),
            ),
            events = DutyEventType.entries.mapIndexed { index, type ->
                DutyEvent("event-$index", type, now.plusSeconds(index.toLong()), if (index == 0) place else null, null)
            },
        )

        repository.save(session)

        assertEquals(session, repository.findById("duty-1"))
    }

    @Test fun appendsModeChangeEventWithNullPlace() = runBlocking {
        val now = Instant.parse("2026-09-03T00:00:00Z")
        repository.save(DutySession("duty-2", now, null))
        val event = DutyEvent("event-mode", DutyEventType.SAFETY_ROAD_MODE_CHANGE, now, null, null, SafetyRoadModeChange(RoadMode.GENERAL, RoadMode.HIGHWAY1))

        repository.appendEvent("duty-2", event)

        assertEquals(event, repository.findById("duty-2")!!.events.single())
    }
}
