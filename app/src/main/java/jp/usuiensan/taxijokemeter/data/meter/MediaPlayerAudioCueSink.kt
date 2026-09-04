package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.media.MediaPlayer

class MediaPlayerAudioCueSink(context: Context) : StartAwareAudioCueSink {
    private val appContext = context.applicationContext

    override fun play(resourceId: Int, onStarted: () -> Unit, onComplete: () -> Unit) {
        val player = try {
            MediaPlayer.create(appContext, resourceId)
        } catch (_: RuntimeException) {
            null
        }
        if (player == null) {
            onComplete()
            return
        }
        val playerInstance = System.identityHashCode(player)
        var completed = false
        fun complete() {
            if (completed) return
            completed = true
            android.util.Log.d(TAG, "[SAFETY-AUDIO] release playerInstance=$playerInstance releasedAt=${android.os.SystemClock.elapsedRealtime()}")
            player.release()
            onComplete()
        }
        player.setOnCompletionListener { complete() }
        player.setOnErrorListener { _, what, extra ->
            android.util.Log.e(TAG, "[SAFETY-AUDIO] playbackError playerInstance=$playerInstance what=$what extra=$extra")
            complete(); true
        }
        try {
            player.start()
            android.util.Log.d(TAG, "[SAFETY-AUDIO] mediaPlayerStart mediaPlayerInstance=$playerInstance startedAt=${android.os.SystemClock.elapsedRealtime()}")
            onStarted()
        } catch (exception: RuntimeException) {
            android.util.Log.e(TAG, "[SAFETY-AUDIO] mediaPlayerStartError mediaPlayerInstance=$playerInstance", exception)
            complete()
        }
    }

    private companion object { const val TAG = "MediaPlayerAudioCueSink" }
}
