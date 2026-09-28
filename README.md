# Mode Avion Planifié (Android 10+)

Application Android native en **Kotlin / Jetpack Compose** avec **Shizuku** pour planifier l'activation et la désactivation automatique du mode avion.

## Contrainte spécifique Android 10 :
Sur Android 10, Shizuku nécessite une activation via câble USB et commande ADB sur ordinateur à chaque démarrage :
```bash
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```
