package com.modeavion.planifie.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_NEEDED,
    READY
}

class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE_PERMISSION = 1001

        @Volatile
        private var instance: ShizukuManager? = null

        fun getInstance(context: Context): ShizukuManager {
            return instance ?: synchronized(this) {
                instance ?: ShizukuManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _shizukuState = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    private var shellService: IShizukuShellService? = null
    private var isBinding = false

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Shizuku UserService connecté")
            shellService = IShizukuShellService.Stub.asInterface(service)
            isBinding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Shizuku UserService déconnecté")
            shellService = null
            isBinding = false
        }
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Permission Shizuku résultat: granted=$granted")
            refreshState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder reçu")
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder mort")
        refreshState()
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Impossible d'attacher les listeners Shizuku", e)
        }
        refreshState()
    }

    fun refreshState() {
        val newState = checkCurrentStatus()
        _shizukuState.value = newState
        if (newState == ShizukuState.READY && shellService == null && !isBinding) {
            bindUserService()
        }
    }

    private fun checkCurrentStatus(): ShizukuState {
        // 1. Vérifier si l'application Shizuku est installée
        val isInstalled = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        if (!isInstalled) {
            return ShizukuState.NOT_INSTALLED
        }

        // 2. Vérifier si le service Shizuku tourne (pingBinder)
        val isRunning = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

        if (!isRunning) {
            return ShizukuState.NOT_RUNNING
        }

        // 3. Vérifier les permissions
        val hasPermission = try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }

        return if (hasPermission) ShizukuState.READY else ShizukuState.PERMISSION_NEEDED
    }

    fun requestPermission() {
        try {
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "Version de Shizuku trop ancienne")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            } else {
                refreshState()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur lors de la demande de permission Shizuku", e)
        }
    }

    fun openShizukuApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openPlayStoreForShizuku()
            }
        } catch (e: Exception) {
            openPlayStoreForShizuku()
        }
    }

    fun openPlayStoreForShizuku() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    private fun bindUserService() {
        try {
            isBinding = true
            val userServiceArgs = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuUserService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shizuku_service")
                .debuggable(true)
                .version(1)

            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur liaison Shizuku UserService", e)
            isBinding = false
        }
    }

    suspend fun setAirplaneMode(enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val stateInt = if (enable) 1 else 0
        val stateBool = enable.toString()

        // 1. Tenter via le service bindé si disponible
        try {
            shellService?.let { service ->
                val ok = service.setAirplaneMode(enable)
                if (ok) return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Échec appel UserService, bascule sur Shizuku.newProcess", e)
        }

        // 2. Exécution directe via Shizuku.newProcess (UID 2000)
        try {
            val cmd1 = "settings put global airplane_mode_on $stateInt"
            val cmd2 = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $stateBool"
            val fullCmd = "$cmd1 && $cmd2"

            val process = Shizuku.newProcess(arrayOf("sh", "-c", fullCmd), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Log.i(TAG, "Mode avion changé avec succès via Shizuku ($enable)")
                Result.success(true)
            } else {
                val errorMsg = "Code shell Shizuku: $exitCode. Output: $output"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erreur Shizuku.newProcess", e)
            Result.failure(Exception("Erreur Shizuku: ${e.message}", e))
        }
    }

    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (e: Exception) {
            false
        }
    }
}