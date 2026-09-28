package com.modeavion.planifie.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.modeavion.planifie.data.ScheduleRepository
import com.modeavion.planifie.data.model.ActionLog
import com.modeavion.planifie.scheduler.AirplaneAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Récepteur appelé lorsque l'utilisateur ou le réseau modifie l'heure ou le fuseau horaire du téléphone.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ScheduleRepository(context)
                val config = repository.scheduleConfigFlow.first()

                if (config.isEnabled) {
                    val scheduler = AirplaneAlarmScheduler(context)
                    val nextEvent = scheduler.scheduleNextAlarm(config)

                    repository.addLog(
                        ActionLog(
                            actionType = ActionLog.ActionType.ALARM_SCHEDULED,
                            details = "Changement d'heure/fuseau horaire. Prochaine alarme recalculée pour ${nextEvent?.triggerTime}",
                            success = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("TimeChangeReceiver", "Erreur", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}