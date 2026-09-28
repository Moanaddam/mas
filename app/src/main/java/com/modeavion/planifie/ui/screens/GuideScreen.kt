package com.modeavion.planifie.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GuideScreen(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adbCommand = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Commande ADB Shizuku", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Commande ADB copiée dans le presse-papiers !", Toast.LENGTH_SHORT).show()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Guide d'activation", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Protocole spécifique Android 10 (USB + ADB)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Avertissement Android 10
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Sur Android 10, le débogage sans fil autonome n'existe pas. L'activation nécessite un câble USB relié à un PC et l'exécution d'une commande ADB après chaque redémarrage.",
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            StepItem("1", "Activer le débogage USB", "Paramètres > À propos > tapez 7 fois sur Numéro de build, puis activez Débogage USB dans Système > Options développeur.")
            Spacer(Modifier.height(16.dp))
            StepItem("2", "Relier le téléphone au PC", "Branchez le câble USB et cochez 'Toujours autoriser depuis cet ordinateur' sur le téléphone.")
            Spacer(Modifier.height(16.dp))
            StepItem("3", "Lancer la commande ADB", "Depuis votre terminal PC / PowerShell :")

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(12.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Terminal PC / PowerShell", fontSize = 11.sp, color = Color.Gray)
                        IconButton(onClick = { copyToClipboard(adbCommand) }) {
                            Icon(Icons.Default.ContentCopy, "Copier", tint = Color(0xFF38BDF8))
                        }
                    }
                    Text(adbCommand, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            StepItem("4", "Autoriser l'application", "Revenez dans l'application et cliquez sur 'Demander la permission'.")

            Spacer(Modifier.height(24.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("J'ai compris, revenir à l'application")
            }
        }
    }
}