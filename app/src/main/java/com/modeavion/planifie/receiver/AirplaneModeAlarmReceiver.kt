package com.modeavion.planifie.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.modeavion.planifie.data.ScheduleRepository
import com.modeavion.planifie.data.model.ActionLog
import com.modeavion.planifie.scheduler.AirplaneAlarmScheduler
import com.modeavion.planifie.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Récepteur appelé par AlarmManager à l'heure exacte pour basculer le mode avion.
 */
class AirplaneModeAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AirplaneAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val targetState = intent.getBooleanExtra(AirplaneAlarmScheduler.EXTRA_TARGET_STATE, false)
        Log.i(TAG, "Alarme reçue ! Basculement mode avion demandé -> $targetState")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val repository = ScheduleRepository(context)
            val shizukuManager = ShizukuManager.getInstance(context)
            val scheduler = AirplaneAlarmScheduler(context)

            try {
                // 1. Exécuter le basculement via Shizuku
                val result = shizukuManager.setAirplaneMode(targetState)
                val success = result.isSuccess

                val logType = if (targetState) {
                    ActionLog.ActionType.AIRPLANE_MODE_ENABLED
                } else {
                    ActionLog.ActionType.AIRPLANE_MODE_DISABLED
                }

                val details = if (success) {
                    "Mode avion ${if (targetState) "ACTIVÉ" else "DÉSACTIVÉ"} avec succès via Shizuku (Alarme)"
                } else {
                    "Échec d'application: ${result.exceptionOrNull()?.message ?: "Erreur Shizuku"}"
                }

                // 2. Enregistrer dans l'historique
                repository.addLog(
                    ActionLog(
                        actionType = logType,
                        details = details,
                        success = success
                    )
                )

                // 3. Re-planifier immédiatement la prochaine transition
                val config = repository.scheduleConfigFlow.first()
                if (config.isEnabled) {
                    val nextEvent = scheduler.scheduleNextAlarm(config)
                    if (nextEvent != null) {
                        repository.addLog(
                            ActionLog(
                                actionType = ActionLog.ActionType.ALARM_SCHEDULED,
                                details = "Prochaine alarme : ${nextEvent.description} à ${nextEvent.triggerTime}",
                                success = true
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur dans AirplaneModeAlarmReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}