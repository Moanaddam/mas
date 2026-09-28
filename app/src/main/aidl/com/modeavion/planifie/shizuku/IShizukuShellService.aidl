package com.modeavion.planifie.shizuku;

interface IShizukuShellService {
    /**
     * Exécute une commande shell avec les privilèges ADB (UID 2000).
     */
    String executeCommand(String command);

    /**
     * Active ou désactive le mode avion du système Android.
     */
    boolean setAirplaneMode(boolean enable);

    /**
     * Arrête le service utilisateur Shizuku.
     */
    void destroy();
}