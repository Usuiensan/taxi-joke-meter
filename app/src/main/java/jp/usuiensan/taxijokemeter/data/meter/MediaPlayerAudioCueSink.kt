package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context
import android.media.MediaPlayer

class MediaPlayerAudioCueSink(context: Context) : AudioCueSink {
    private val appContext = context.applicationContext

    override fun play(resourceId: Int, onComplete: () -> Unit) {
        val player = try {
            MediaPlayer.create(appContext, resourceId)
        } catch (_: RuntimeException) {
            null
        }
        if (player == null) {
            onComplete()
            return
        }
        var completed = false
        fun complete() {
            if (completed) return
            completed = true
            player.release()
            onComplete()
        }
        player.setOnCompletionListener { complete() }
        player.setOnErrorListener { _, _, _ -> complete(); true }
        player.start()
    }
}
