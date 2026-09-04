package jp.usuiensan.taxijokemeter.data.meter

import android.util.Log

fun interface AudioCueSink {
    fun play(resourceId: Int, onComplete: () -> Unit)
}

interface StartAwareAudioCueSink : AudioCueSink {
    fun play(resourceId: Int, onStarted: () -> Unit, onComplete: () -> Unit)

    override fun play(resourceId: Int, onComplete: () -> Unit) = play(resourceId, {}, onComplete)
}

object ProcessSafetyPlaybackGuard : SafetyPlaybackGuard {
    private const val SAFETY_GUARD_MILLIS = 1_000L
    private var active = false
    private var lastStartedAt: Long? = null

    @Synchronized
    override fun isBlocked(now: Long): Boolean = synchronized(this) {
        active || lastStartedAt?.let { now - it < SAFETY_GUARD_MILLIS } == true
    }

    @Synchronized
    override fun reserve(now: Long): Boolean {
        if (active || lastStartedAt?.let { now - it < SAFETY_GUARD_MILLIS } == true) return false
        active = true
        return true
    }

    @Synchronized
    override fun started(now: Long) {
        lastStartedAt = now
    }

    @Synchronized
    override fun finished() {
        active = false
    }
}

class AudioCuePlayer(
    private val resolveResource: (MeterAudioCue) -> Int?,
    private val sink: AudioCueSink,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val debounceMillis: Long = 5_000L,
    private val safetyPlaybackGuard: SafetyPlaybackGuard = LocalSafetyPlaybackGuard(),
) {
    private val queue = ArrayDeque<MeterAudioCue>()
    private val lastQueuedAt = mutableMapOf<MeterAudioCue, Long>()
    private var playing = false

    fun enqueue(cue: MeterAudioCue, sourceTag: String = "unknown") {
        val now = nowMillis()
        debugLog("[SAFETY-AUDIO] playRequest cue=$cue source=$sourceTag requestAt=$now audioPlayerInstance=$playerInstanceId")
        if (cue.isSafetyNotification() && safetyPlaybackGuard.isBlocked(now)) {
            debugLog("[SAFETY-AUDIO] skip=safety-global-debounce cue=$cue source=$sourceTag requestAt=$now audioPlayerInstance=$playerInstanceId")
            return
        }
        if (lastQueuedAt[cue]?.let { now - it < debounceMillis } == true) {
            debugLog("[SAFETY-AUDIO] skip=same-cue-debounce cue=$cue source=$sourceTag requestAt=$now audioPlayerInstance=$playerInstanceId")
            return
        }
        lastQueuedAt[cue] = now
        queue.addLast(cue)
        playNext()
    }

    private fun playNext() {
        if (playing) return
        val cue = queue.removeFirstOrNull() ?: return
        val resourceId = resolveResource(cue)
        if (resourceId == null || resourceId == 0) {
            if (cue.isSafetyNotification()) safetyPlaybackGuard.finished()
            playNext()
            return
        }
        if (cue.isSafetyNotification() && !safetyPlaybackGuard.reserve(nowMillis())) {
            debugLog("[SAFETY-AUDIO] skip=safety-global-debounce-while-queued cue=$cue audioPlayerInstance=$playerInstanceId")
            playNext()
            return
        }
        playing = true
        var started = false
        val onStarted = {
            started = true
            if (cue.isSafetyNotification()) safetyPlaybackGuard.started(nowMillis())
            debugLog("[SAFETY-AUDIO] actualPlaybackStart cue=$cue playbackAt=${nowMillis()} audioPlayerInstance=$playerInstanceId")
            Unit
        }
        val onComplete = {
            playing = false
            if (cue.isSafetyNotification()) safetyPlaybackGuard.finished()
            debugLog("[SAFETY-AUDIO] playbackComplete cue=$cue completedAt=${nowMillis()} audioPlayerInstance=$playerInstanceId started=$started")
            playNext()
        }
        if (sink is StartAwareAudioCueSink) sink.play(resourceId, onStarted, onComplete) else {
            sink.play(resourceId, onComplete)
            onStarted()
        }
    }

    private companion object { const val TAG = "AudioCuePlayer" }

    private val playerInstanceId = System.identityHashCode(this)

    private fun debugLog(message: String) {
        try { Log.d(TAG, message) } catch (_: RuntimeException) { }
    }
}

interface SafetyPlaybackGuard {
    fun isBlocked(now: Long): Boolean
    fun reserve(now: Long): Boolean
    fun started(now: Long)
    fun finished()
}

private class LocalSafetyPlaybackGuard : SafetyPlaybackGuard {
    private var active = false
    private var lastStartedAt: Long? = null

    override fun isBlocked(now: Long) = active || lastStartedAt?.let { now - it < 1_000L } == true
    override fun reserve(now: Long): Boolean {
        if (isBlocked(now)) return false
        active = true
        return true
    }
    override fun started(now: Long) { lastStartedAt = now }
    override fun finished() { active = false }
}
