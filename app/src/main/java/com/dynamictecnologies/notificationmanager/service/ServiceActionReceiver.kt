package com.dynamictecnologies.notificationmanager.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receptor de acciones de usuario en las notificaciones del servicio.
 * 
 * Maneja 3 acciones:
 * 1. STOP: Usuario presiona "DETENER" en notificación running
 * 2. RESTART: Usuario presiona "Reiniciar" en notificación stopped
 * 3. ACKNOWLEDGE: Usuario presiona "Entendido" en notificación stopped
 */
@AndroidEntryPoint
class ServiceActionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var actionHandler: ServiceActionHandler
    
    companion object {
        private const val TAG = "ServiceAction"
        
        const val ACTION_STOP_SERVICE = "com.dynamictecnologies.ACTION_STOP"
        const val ACTION_RESTART_SERVICE = "com.dynamictecnologies.ACTION_RESTART"
        const val ACTION_ACKNOWLEDGE = "com.dynamictecnologies.ACTION_ACKNOWLEDGE"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Action received: ${intent.action}")
        
        when (intent.action) {
            ACTION_STOP_SERVICE -> actionHandler.handleStopService(context)
            ACTION_RESTART_SERVICE -> actionHandler.handleRestartService(context)
            ACTION_ACKNOWLEDGE -> actionHandler.handleAcknowledge(context)
        }
    }
}
