package com.dynamictecnologies.notificationmanager.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lógica pura de manejo de acciones de usuario sobre el servicio.
 * Desacoplado de BroadcastReceiver para facilitar el testing sin conflictos de Hilt.
 */
@Singleton
class ServiceActionHandler @Inject constructor(
    private val serviceStateManager: ServiceStateManager,
    private val serviceNotificationManager: ServiceNotificationManager
) {
    companion object {
        private const val TAG = "ServiceActionHandler"
    }

    /**
     * Maneja el botón DETENER.
     */
    fun handleStopService(context: Context) {
        Log.d(TAG, "Usuario presionó DETENER")
        
        serviceStateManager.setStateSync(ServiceStateManager.ServiceState.STOPPED)
        
        try {
            context.stopService(Intent(context, NotificationForegroundService::class.java))
            Log.d(TAG, "Servicio detenido exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicio: ${e.message}")
        }
        
        serviceNotificationManager.showStoppedNotification(
            ServiceNotificationManager.StopReason.USER_STOP
        )
    }

    /**
     * Maneja el botón REINICIAR.
     */
    fun handleRestartService(context: Context) {
        Log.d(TAG, "Usuario presionó REINICIAR")
        
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.resetStoppedCounter()
        
        try {
            val intent = Intent(context, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Servicio reiniciado exitosamente")
            serviceNotificationManager.hideAllNotifications()
        } catch (e: Exception) {
            Log.e(TAG, "Error reiniciando servicio: ${e.message}")
            serviceNotificationManager.showStoppedNotification(ServiceNotificationManager.StopReason.ERROR)
        }
    }

    /**
     * Maneja el botón ENTENDIDO.
     */
    fun handleAcknowledge(context: Context) {
        Log.d(TAG, "Usuario presionó ENTENDIDO - Deteniendo todo...")
        
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        
        try {
            context.stopService(Intent(context, NotificationForegroundService::class.java))
            Log.d(TAG, "Servicio detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicio: ${e.message}")
        }
        
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            Log.d(TAG, "AlarmManager aware of DISABLED state")
        } catch (e: Exception) {
            Log.e(TAG, "Error con AlarmManager: ${e.message}")
        }
        
        serviceNotificationManager.hideAllNotifications()
    }
}
