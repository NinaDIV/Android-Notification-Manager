package com.dynamictecnologies.notificationmanager.service

import android.content.Context

/**
 * Gestor de estados del servicio de notificaciones.
 * 
 * Controla:
 * - Estado actual del servicio (RUNNING/STOPPED/DISABLED)
 * - Contador de notificaciones "stopped" mostradas
 * - Lógica de cuándo mostrar notificaciones
 * 
 * Estados:
 * - RUNNING: Servicio activo normal
 * - STOPPED: Servicio detenido temporalmente
 * - DISABLED: Usuario eligió "Entendido", no molestar hasta que abra app
 */
object ServiceStateManager {
    
    private const val PREFS_NAME = "service_state_prefs"
    private const val KEY_CURRENT_STATE = "current_state"
    private const val KEY_STOPPED_SHOWN = "stopped_notification_shown"
    private const val KEY_STOPPED_COUNT = "stopped_notification_count"
    private const val KEY_LAST_STATE_CHANGE = "last_state_change_time"
    private const val KEY_DEGRADED_REASON = "degraded_reason"
    
    /**
     * Estados del semáforo de servicio:
     * - RUNNING (VERDE): Servicio activo, internet OK, permisos OK
     * - DEGRADED (AMARILLO): Servicio activo pero con problemas (permisos revocados, sin internet)
     * - STOPPED (ROJO): Servicio detenido inesperadamente
     * - DISABLED (GRIS): Usuario eligió "Entendido", no molestar
     */
    enum class ServiceState {
        RUNNING,    // [OK] Operativo completo
        DEGRADED,   // [WARN] Advertencia: permisos o conectividad degradada
        STOPPED,    // [ERROR] Servicio detenido
        DISABLED    // Usuario eligió "Entendido"
    }
    
    /**
     * Razones de estado DEGRADED
     */
    enum class DegradedReason {
        NONE,
        PERMISSION_REVOKED,
        NO_INTERNET,
        MQTT_DISCONNECTED,
        INITIALIZATION_FAILED
    }
    
    /**
     * Obtiene el estado actual del servicio.
     */
    fun getCurrentState(context: Context): ServiceState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stateName = prefs.getString(KEY_CURRENT_STATE, ServiceState.RUNNING.name)
        return try {
            ServiceState.valueOf(stateName ?: ServiceState.RUNNING.name)
        } catch (e: IllegalArgumentException) {
            ServiceState.RUNNING
        }
    }
    
    /**
     * Establece el estado actual del servicio.
     */
    fun setState(context: Context, state: ServiceState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Establece el estado actual del servicio de forma SÍNCRONA.
     * Usar cuando es crítico que el estado esté persistido antes de continuar
     * (ej: antes de detener un servicio para evitar race conditions con auto-restart).
     */
    fun setStateSync(context: Context, state: ServiceState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .commit() // Síncrono en lugar de apply()
    }

    
    /**
     * Verifica si se puede mostrar la notificación de "servicio detenido".
     * Solo se muestra una vez por sesión hasta que se vuelva a abrir la app.
     */
    fun canShowStoppedNotification(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = getCurrentState(context)
        val alreadyShown = prefs.getBoolean(KEY_STOPPED_SHOWN, false)
        
        // Solo mostrar si:
        // 1. Estado actual es RUNNING (servicio debería estar activo)
        // 2. No se ha mostrado ya en esta sesión
        // 3. No está en estado DISABLED (usuario no quiere el servicio)
        return currentState == ServiceState.RUNNING && !alreadyShown
    }
    
    /**
     * Marca que la notificación de "servicio detenido" ya fue mostrada.
     */
    fun markStoppedNotificationShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_STOPPED_SHOWN, true)
            .putInt(KEY_STOPPED_COUNT, getStoppedCount(context) + 1)
            .putLong("last_stopped_time", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Resetea el contador de notificaciones mostradas.
     * Se llama cuando usuario presiona "Reiniciar" o cuando abre la app.
     */
    fun resetStoppedCounter(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STOPPED_SHOWN, false)
            .apply()
    }
    
    /**
     * Obtiene el número total de veces que se ha mostrado la notificación de stopped.
     */
    fun getStoppedCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_STOPPED_COUNT, 0)
    }
    
    /**
     * Se llama cuando MainActivity.onCreate() para resetear estado si es necesario.
     */
    fun resetOnAppOpen(context: Context) {
        val currentState = getCurrentState(context)
        
        // Si el usuario eligió "Entendido" pero volvió a abrir la app,
        // asumimos que quiere usar la app de nuevo
        if (currentState == ServiceState.DISABLED) {
            setState(context, ServiceState.RUNNING)
        }
        
        // Resetear contador para dar otra oportunidad de mostrar notificación
        resetStoppedCounter(context)
    }
    
    /**
     * Obtiene tiempo desde el último cambio de estado (para debugging).
     */
    fun getTimeSinceLastStateChange(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastChange = prefs.getLong(KEY_LAST_STATE_CHANGE, 0)
        return if (lastChange > 0) {
            System.currentTimeMillis() - lastChange
        } else {
            0
        }
    }
    
    /**
     * Establece el estado DEGRADED (amarillo) con razón específica.
     * Usar cuando el servicio sigue corriendo pero tiene problemas.
     */
    fun setDegradedState(context: Context, reason: DegradedReason) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_STATE, ServiceState.DEGRADED.name)
            .putString(KEY_DEGRADED_REASON, reason.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Obtiene la razón del estado DEGRADED actual.
     */
    fun getDegradedReason(context: Context): DegradedReason {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val reasonName = prefs.getString(KEY_DEGRADED_REASON, DegradedReason.NONE.name)
        return try {
            DegradedReason.valueOf(reasonName ?: DegradedReason.NONE.name)
        } catch (e: IllegalArgumentException) {
            DegradedReason.NONE
        }
    }
    
    /**
     * Limpia la razón de DEGRADED y vuelve a RUNNING.
     */
    fun clearDegraded(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_STATE, ServiceState.RUNNING.name)
            .putString(KEY_DEGRADED_REASON, DegradedReason.NONE.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
}
