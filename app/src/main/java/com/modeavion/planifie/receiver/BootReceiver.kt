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
 * Récepteur appelé après le redémarrage du smartphone.
 *
 * NOTE IMPORTANTE POUR ANDROID 10 :
 * Après un redémarrage, Shizuku doit obligatoirement être relancé via la commande ADB
 * depuis un ordinateur, car Android 10 ne supporte pas le débogage sans fil autonome.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootReceiver déclenché: $action")

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
                            details = "Redémarrage détecté. Alarmes reprogrammées. Rappel : Sur Android 10, relancez Shizuku via ADB si nécessaire.",
                            success = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur dans BootReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}