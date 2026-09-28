package com.modeavion.planifie.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.modeavion.planifie.data.model.ScheduleConfig
import com.modeavion.planifie.receiver.AirplaneModeAlarmReceiver
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduledEvent(
    val triggerTime: LocalDateTime,
    val targetAirplaneModeState: Boolean,
    val description: String
)

class AirplaneAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AirplaneAlarmScheduler"
        const val REQUEST_CODE_ALARM = 4200
        const val EXTRA_TARGET_STATE = "extra_target_state"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Calcule le prochain événement d'activation ou désactivation en fonction de la configuration.
     * Gère avec précision les plages traversant minuit (ex: 23:00 -> 07:00).
     */
    fun getNextEvent(config: ScheduleConfig, now: LocalDateTime = LocalDateTime.now()): ScheduledEvent? {
        if (!config.isEnabled || config.activeDays.isEmpty()) {
            return null
        }

        val candidates = mutableListOf<ScheduledEvent>()
        val today = now.toLocalDate()

        // Parcourir les 14 prochains jours pour trouver la transition la plus proche
        for (i in 0..14) {
            val date = today.plusDays(i.toLong())
            val dayOfWeek = date.dayOfWeek.value // 1 = Lundi ... 7 = Dimanche

            if (config.activeDays.contains(dayOfWeek)) {
                val startTime = LocalDateTime.of(date, LocalTime.of(config.startHour, config.startMinute))
                val endTime = if (config.crossesMidnight) {
                    LocalDateTime.of(date.plusDays(1), LocalTime.of(config.endHour, config.endMinute))
                } else {
                    LocalDateTime.of(date, LocalTime.of(config.endHour, config.endMinute))
                }

                if (startTime.isAfter(now)) {
                    candidates.add(
                        ScheduledEvent(
                            triggerTime = startTime,
                            targetAirplaneModeState = true,
                            description = "Activation du mode avion à ${config.formattedStartTime()}"
                        )
                    )
                }

                if (endTime.isAfter(now)) {
                    candidates.add(
                        ScheduledEvent(
                            triggerTime = endTime,
                            targetAirplaneModeState = false,
                            description = "Désactivation du mode avion à ${config.formattedEndTime()}"
                        )
                    )
                }
            }
        }

        return candidates.minByOrNull { it.triggerTime }
    }

    fun scheduleNextAlarm(config: ScheduleConfig): ScheduledEvent? {
        cancelExistingAlarm()

        if (!config.isEnabled) return null

        val nextEvent = getNextEvent(config) ?: return null

        val epochMillis = nextEvent.triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = createPendingIntent(nextEvent.targetAirplaneModeState)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                epochMillis,
                pendingIntent
            )
            Log.i(TAG, "Alarme programmée pour ${nextEvent.triggerTime} (cible: ${nextEvent.targetAirplaneModeState})")
        } catch (e: SecurityException) {
            Log.e(TAG, "Erreur permission alarme exacte, fallback sur alarme standard", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pendingIntent)
        }

        return nextEvent
    }

    fun cancelExistingAlarm() {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            Intent(context, AirplaneModeAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun createPendingIntent(targetState: Boolean): PendingIntent {
        val intent = Intent(context, AirplaneModeAlarmReceiver::class.java).apply {
            action = "com.modeavion.planifie.ACTION_SET_AIRPLANE_MODE"
            putExtra(EXTRA_TARGET_STATE, targetState)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}