package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCuePlayerTest {
    @Test fun differentSafetyCueWithinOneSecondIsSkipped() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.HARD_ACCELERATION) 10 else 20 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        now += 400L
        player.enqueue(MeterAudioCue.HARD_DECELERATION)
        completions.removeFirst()()

        assertEquals(listOf(10), played)
    }

    @Test fun differentSafetyCueAfterOneSecondIsPlayed() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.HARD_ACCELERATION) 10 else 20 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        completions.removeFirst()()
        now += 1_200L
        player.enqueue(MeterAudioCue.HARD_DECELERATION)

        assertEquals(listOf(10, 20), played)
    }

    @Test fun overspeedWithinOneSecondIsSkipped() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.HARD_ACCELERATION) 10 else 20 },
            sink = AudioCueSink { resourceId, _ -> played += resourceId },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        now += 400L
        player.enqueue(MeterAudioCue.OVERSPEED)

        assertEquals(listOf(10), played)
    }

    @Test fun paymentWithinOneSecondOfSafetyIsNotSkipped() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.HARD_ACCELERATION) 10 else 30 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        now += 400L
        player.enqueue(MeterAudioCue.PAYMENT)
        completions.removeFirst()()

        assertEquals(listOf(10, 30), played)
    }

    @Test fun safetyAfterPaymentIsNotSkippedBySafetyGuard() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.PAYMENT) 30 else 10 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.PAYMENT)
        completions.removeFirst()()
        now += 400L
        player.enqueue(MeterAudioCue.HARD_ACCELERATION)

        assertEquals(listOf(30, 10), played)
    }

    @Test fun safetySkippedByGuardDoesNotUpdateItsDebounceHistory() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { cue -> if (cue == MeterAudioCue.HARD_ACCELERATION) 10 else 20 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        now += 400L
        player.enqueue(MeterAudioCue.HARD_DECELERATION)
        completions.removeFirst()()
        now += 800L
        player.enqueue(MeterAudioCue.HARD_DECELERATION)

        assertEquals(listOf(10, 20), played)
    }

    @Test fun sameSafetyCueKeepsExistingFiveSecondDebounce() {
        var now = 1_000L
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { 10 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { now },
        )

        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        completions.removeFirst()()
        now += 3_000L
        player.enqueue(MeterAudioCue.HARD_ACCELERATION)
        now += 3_000L
        player.enqueue(MeterAudioCue.HARD_ACCELERATION)

        assertEquals(listOf(10, 10), played)
    }

    @Test fun onlyExplicitSafetyCuesUseSafetyGuard() {
        assertEquals(true, MeterAudioCue.OVERSPEED.isSafetyNotification())
        assertEquals(true, MeterAudioCue.HARD_ACCELERATION.isSafetyNotification())
        assertEquals(true, MeterAudioCue.HARD_DECELERATION.isSafetyNotification())
        assertEquals(false, MeterAudioCue.OCCUPIED.isSafetyNotification())
        assertEquals(false, MeterAudioCue.PAYMENT.isSafetyNotification())
        assertEquals(false, MeterAudioCue.RETURN_TO_GENERAL.isSafetyNotification())
    }

    @Test fun queuedCuesDoNotOverlapAndDuplicateCueIsDebounced() {
        val played = mutableListOf<Int>()
        val completions = ArrayDeque<() -> Unit>()
        val player = AudioCuePlayer(
            resolveResource = { 10 },
            sink = AudioCueSink { resourceId, onComplete -> played += resourceId; completions += onComplete },
            nowMillis = { 1_000L },
        )

        player.enqueue(MeterAudioCue.OCCUPIED)
        player.enqueue(MeterAudioCue.PAYMENT)
        player.enqueue(MeterAudioCue.PAYMENT)
        assertEquals(listOf(10), played)

        completions.removeFirst()()
        assertEquals(listOf(10, 10), played)
    }

    @Test fun missingResourceIsSkippedWithoutCallingSink() {
        var playCount = 0
        AudioCuePlayer(
            resolveResource = { null },
            sink = AudioCueSink { _, _ -> playCount++ },
        ).enqueue(MeterAudioCue.PAYMENT)

        assertEquals(0, playCount)
    }

    @Test fun onlyNewOccupiedAndPaymentTransitionsProduceCues() {
        assertEquals(MeterAudioCue.OCCUPIED, meterAudioCueForTransition(MeterState.EMPTY, MeterState.OCCUPIED))
        assertEquals(MeterAudioCue.OCCUPIED, meterAudioCueForTransition(MeterState.PAYMENT, MeterState.OCCUPIED))
        assertEquals(MeterAudioCue.PAYMENT, meterAudioCueForTransition(MeterState.OCCUPIED, MeterState.PAYMENT))
        assertEquals(null, meterAudioCueForTransition(MeterState.OCCUPIED, MeterState.OCCUPIED))
    }

    @Test fun safetyEventTypesMapToTheirCues() {
        assertEquals(MeterAudioCue.OVERSPEED, SafetyEventType.OVERSPEED.toMeterAudioCue())
        assertEquals(MeterAudioCue.HARD_ACCELERATION, SafetyEventType.HARD_ACCELERATION.toMeterAudioCue())
        assertEquals(MeterAudioCue.HARD_DECELERATION, SafetyEventType.HARD_DECELERATION.toMeterAudioCue())
    }

    @Test fun safetyCuesUseTheSharedNotificationBeepResource() {
        assertEquals("safety_notification_beep", MeterAudioResources.resourceName(MeterAudioCue.OVERSPEED))
        assertEquals("safety_notification_beep", MeterAudioResources.resourceName(MeterAudioCue.HARD_ACCELERATION))
        assertEquals("safety_notification_beep", MeterAudioResources.resourceName(MeterAudioCue.HARD_DECELERATION))
    }
}
