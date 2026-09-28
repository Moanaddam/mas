package com.modeavion.planifie.shizuku

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Service Shizuku privilégié s'exécutant avec les droits ADB (UID 2000).
 * Ce service permet d'exécuter des commandes système Android sans accès Root.
 */
class ShizukuUserService : IShizukuShellService.Stub() {

    companion object {
        private const val TAG = "ShizukuUserService"
    }

    override fun executeCommand(command: String): String {
        Log.d(TAG, "Exécution de la commande shell: $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exécution shell", e)
            "ERROR: ${e.message}"
        }
    }

    override fun setAirplaneMode(enable: Boolean): Boolean {
        val stateInt = if (enable) 1 else 0
        val stateBool = enable.toString()

        Log.i(TAG, "Modification du mode avion -> $enable (stateInt=$stateInt)")

        return try {
            val cmd1 = "settings put global airplane_mode_on $stateInt"
            val cmd2 = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $stateBool"
            val fullCommand = "$cmd1 && $cmd2"

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", fullCommand))
            val exitCode = process.waitFor()

            Log.i(TAG, "Commandes exécutées avec code de sortie: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Échec du changement de mode avion via Shizuku", e)
            false
        }
    }

    override fun destroy() {
        Log.i(TAG, "Arrêt du service Shizuku")
        exitProcess(0)
    }
}