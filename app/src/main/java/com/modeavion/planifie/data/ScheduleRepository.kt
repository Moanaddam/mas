package com.modeavion.planifie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.modeavion.planifie.data.model.ActionLog
import com.modeavion.planifie.data.model.ScheduleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "airplane_mode_prefs")

class ScheduleRepository(private val context: Context) {

    private object PreferencesKeys {
        val START_HOUR = intPreferencesKey("start_hour")
        val START_MINUTE = intPreferencesKey("start_minute")
        val END_HOUR = intPreferencesKey("end_hour")
        val END_MINUTE = intPreferencesKey("end_minute")
        val ACTIVE_DAYS = stringSetPreferencesKey("active_days")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val RECENT_LOGS = stringPreferencesKey("recent_logs")
    }

    val scheduleConfigFlow: Flow<ScheduleConfig> = context.dataStore.data.map { preferences ->
        val startHour = preferences[PreferencesKeys.START_HOUR] ?: 23
        val startMinute = preferences[PreferencesKeys.START_MINUTE] ?: 0
        val endHour = preferences[PreferencesKeys.END_HOUR] ?: 7
        val endMinute = preferences[PreferencesKeys.END_MINUTE] ?: 0
        val activeDaysStrings = preferences[PreferencesKeys.ACTIVE_DAYS]
            ?: setOf("1", "2", "3", "4", "5", "6", "7")
        val isEnabled = preferences[PreferencesKeys.IS_ENABLED] ?: false

        val activeDays = activeDaysStrings.mapNotNull { it.toIntOrNull() }.toSet()

        ScheduleConfig(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            activeDays = if (activeDays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else activeDays,
            isEnabled = isEnabled
        )
    }

    suspend fun updateTimeRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.START_HOUR] = startHour
            preferences[PreferencesKeys.START_MINUTE] = startMinute
            preferences[PreferencesKeys.END_HOUR] = endHour
            preferences[PreferencesKeys.END_MINUTE] = endMinute
        }
    }

    suspend fun updateActiveDays(days: Set<Int>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_DAYS] = days.map { it.toString() }.toSet()
        }
    }

    suspend fun setScheduleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ENABLED] = enabled
        }
    }

    val logsFlow: Flow<List<ActionLog>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.RECENT_LOGS] ?: ""
        parseLogs(raw)
    }

    suspend fun addLog(log: ActionLog) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.RECENT_LOGS] ?: ""
            val current = parseLogs(raw).toMutableList()
            current.add(0, log)
            val trimmed = current.take(50)
            preferences[PreferencesKeys.RECENT_LOGS] = serializeLogs(trimmed)
        }
    }

    private fun serializeLogs(logs: List<ActionLog>): String {
        return logs.joinToString(";;;") { "${it.id}|${it.timestamp}|${it.actionType.name}|${it.details}|${it.success}" }
    }

    private fun parseLogs(raw: String): List<ActionLog> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";;;").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 5) {
                try {
                    ActionLog(
                        id = parts[0],
                        timestamp = LocalDateTime.parse(parts[1]),
                        actionType = ActionLog.ActionType.valueOf(parts[2]),
                        details = parts[3],
                        success = parts[4].toBoolean()
                    )
                } catch (e: Exception) { null }
            } else null
        }
    }
}