package jp.usuiensan.taxijokemeter.data.meter

import android.content.Context

object MeterAudioResources {
    private val resourceNames = mapOf(
        MeterAudioCue.OCCUPIED to "meter_occupied",
        MeterAudioCue.PAYMENT to "meter_payment",
        MeterAudioCue.OVERSPEED to "safety_notification_beep",
        MeterAudioCue.RETURN_TO_GENERAL to "roadmode_return_general",
        MeterAudioCue.HARD_ACCELERATION to "safety_notification_beep",
        MeterAudioCue.HARD_DECELERATION to "safety_notification_beep",
    )

    internal fun resourceName(cue: MeterAudioCue): String? = resourceNames[cue]

    fun resourceId(context: Context, cue: MeterAudioCue): Int? =
        context.resources.getIdentifier(resourceName(cue), "raw", context.packageName).takeIf { it != 0 }
}
