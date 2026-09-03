package jp.usuiensan.taxijokemeter.data.meter

import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCuePlayerTest {
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
}
