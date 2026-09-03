package jp.usuiensan.taxijokemeter.data.meter

fun interface AudioCueSink {
    fun play(resourceId: Int, onComplete: () -> Unit)
}

class AudioCuePlayer(
    private val resolveResource: (MeterAudioCue) -> Int?,
    private val sink: AudioCueSink,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val debounceMillis: Long = 5_000L,
) {
    private val queue = ArrayDeque<MeterAudioCue>()
    private val lastQueuedAt = mutableMapOf<MeterAudioCue, Long>()
    private var playing = false

    fun enqueue(cue: MeterAudioCue) {
        val now = nowMillis()
        if (lastQueuedAt[cue]?.let { now - it < debounceMillis } == true) return
        lastQueuedAt[cue] = now
        queue.addLast(cue)
        playNext()
    }

    private fun playNext() {
        if (playing) return
        val cue = queue.removeFirstOrNull() ?: return
        val resourceId = resolveResource(cue)
        if (resourceId == null || resourceId == 0) {
            playNext()
            return
        }
        playing = true
        sink.play(resourceId) {
            playing = false
            playNext()
        }
    }
}
