package com.modeavion.planifie.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modeavion.planifie.data.model.ActionLog
import com.modeavion.planifie.shizuku.ShizukuState
import com.modeavion.planifie.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (uiState.showGuideDialog) {
        GuideScreen(onDismiss = { viewModel.setGuideDialogVisible(false) })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AirplanemodeActive, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Mode Avion Planifié", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshState() }) {
                        Icon(Icons.Default.Refresh, "Rafraîchir")
                    }
                    IconButton(onClick = { viewModel.setGuideDialogVisible(true) }) {
                        Icon(Icons.Default.HelpOutline, "Guide Android 10", tint = Color(0xFFF59E0B))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // État actuel
            item {
                StatusCard(
                    isAirplaneModeOn = uiState.isAirplaneModeOn,
                    shizukuState = uiState.shizukuState,
                    nextEventDesc = uiState.nextEvent?.description,
                    isScheduleEnabled = uiState.scheduleConfig.isEnabled
                )
            }

            // Bannière d'aide si Shizuku n'est pas prêt
            if (uiState.shizukuState != ShizukuState.READY) {
                item {
                    ShizukuWarningBanner(
                        state = uiState.shizukuState,
                        onOpenGuide = { viewModel.setGuideDialogVisible(true) },
                        onOpenApp = { viewModel.openShizukuApp() },
                        onRequestPermission = { viewModel.requestShizukuPermission() }
                    )
                }
            }

            // Interrupteur principal
            item {
                ScheduleToggleCard(
                    isEnabled = uiState.scheduleConfig.isEnabled,
                    onToggle = { viewModel.toggleSchedule(it) }
                )
            }

            // Plage horaire avec badge minuit
            item {
                TimeRangeCard(
                    startHour = uiState.scheduleConfig.startHour,
                    startMinute = uiState.scheduleConfig.startMinute,
                    endHour = uiState.scheduleConfig.endHour,
                    endMinute = uiState.scheduleConfig.endMinute,
                    crossesMidnight = uiState.scheduleConfig.crossesMidnight,
                    onOpenStartTimePicker = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setTimeRange(h, m, uiState.scheduleConfig.endHour, uiState.scheduleConfig.endMinute) },
                            uiState.scheduleConfig.startHour,
                            uiState.scheduleConfig.startMinute,
                            true
                        ).show()
                    },
                    onOpenEndTimePicker = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setTimeRange(uiState.scheduleConfig.startHour, uiState.scheduleConfig.startMinute, h, m) },
                            uiState.scheduleConfig.endHour,
                            uiState.scheduleConfig.endMinute,
                            true
                        ).show()
                    }
                )
            }

            // Jours de la semaine
            item {
                DaysOfWeekCard(
                    activeDays = uiState.scheduleConfig.activeDays,
                    onToggleDay = { viewModel.toggleDay(it) }
                )
            }

            // Boutons d'actions et diagnostic
            item {
                ActionButtonsCard(
                    shizukuState = uiState.shizukuState,
                    isTesting = uiState.isTesting,
                    testMessage = uiState.testMessage,
                    onOpenShizuku = { viewModel.openShizukuApp() },
                    onOpenGuide = { viewModel.setGuideDialogVisible(true) },
                    onRequestPermission = { viewModel.requestShizukuPermission() },
                    onTestNow = { viewModel.testAirplaneModeToggle() }
                )
            }

            // Historique
            item {
                Text("Journal d'activité récent", style = MaterialTheme.typography.titleLarge)
            }

            items(uiState.logs.take(15)) { log ->
                LogItemView(log = log)
            }
        }
    }
}