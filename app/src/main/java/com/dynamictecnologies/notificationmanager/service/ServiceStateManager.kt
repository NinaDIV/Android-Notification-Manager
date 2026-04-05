package com.dynamictecnologies.notificationmanager.service

import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.di.ServicePrefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor de estados del servicio de notificaciones.
 * 
 * Controla:
 * - Estado actual del servicio (RUNNING/STOPPED/DISABLED)
 * - Contador de notificaciones "stopped" mostradas
 * - Lógica de cuándo mostrar notificaciones
 */
@Singleton
class ServiceStateManager @Inject constructor(
    @ServicePrefs private val prefs: SharedPreferences
) {
    
    companion object {
        private const val KEY_CURRENT_STATE = "current_state"
        private const val KEY_STOPPED_SHOWN = "stopped_notification_shown"
        private const val KEY_STOPPED_COUNT = "stopped_notification_count"
        private const val KEY_LAST_STATE_CHANGE = "last_state_change_time"
        private const val KEY_DEGRADED_REASON = "degraded_reason"
    }
    
    enum class ServiceState {
        RUNNING,    // [OK] Operativo completo
        DEGRADED,   // [WARN] Advertencia: permisos o conectividad degradada
        STOPPED,    // [ERROR] Servicio detenido
        DISABLED    // Usuario eligió "Entendido"
    }
    
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
    fun getCurrentState(): ServiceState {
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
    fun setState(state: ServiceState) {
        prefs.edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Establece el estado actual del servicio de forma SÍNCRONA.
     */
    fun setStateSync(state: ServiceState) {
        prefs.edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .commit()
    }

    /**
     * Verifica si se puede mostrar la notificación de "servicio detenido".
     */
    fun canShowStoppedNotification(): Boolean {
        val currentState = getCurrentState()
        val alreadyShown = prefs.getBoolean(KEY_STOPPED_SHOWN, false)
        return currentState == ServiceState.RUNNING && !alreadyShown
    }
    
    /**
     * Marca que la notificación de "servicio detenido" ya fue mostrada.
     */
    fun markStoppedNotificationShown() {
        prefs.edit()
            .putBoolean(KEY_STOPPED_SHOWN, true)
            .putInt(KEY_STOPPED_COUNT, getStoppedCount() + 1)
            .putLong("last_stopped_time", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Resetea el contador de notificaciones mostradas.
     */
    fun resetStoppedCounter() {
        prefs.edit()
            .putBoolean(KEY_STOPPED_SHOWN, false)
            .apply()
    }
    
    /**
     * Obtiene el número total de veces que se ha mostrado la notificación de stopped.
     */
    fun getStoppedCount(): Int {
        return prefs.getInt(KEY_STOPPED_COUNT, 0)
    }
    
    /**
     * Se llama cuando MainActivity.onCreate() para resetear estado si es necesario.
     */
    fun resetOnAppOpen() {
        val currentState = getCurrentState()
        if (currentState == ServiceState.DISABLED) {
            setState(ServiceState.RUNNING)
        }
        resetStoppedCounter()
    }
    
    /**
     * Obtiene tiempo desde el último cambio de estado.
     */
    fun getTimeSinceLastStateChange(): Long {
        val lastChange = prefs.getLong(KEY_LAST_STATE_CHANGE, 0)
        return if (lastChange > 0) {
            System.currentTimeMillis() - lastChange
        } else {
            0
        }
    }
    
    /**
     * Establece el estado DEGRADED (amarillo) con razón específica.
     */
    fun setDegradedState(reason: DegradedReason) {
        prefs.edit()
            .putString(KEY_CURRENT_STATE, ServiceState.DEGRADED.name)
            .putString(KEY_DEGRADED_REASON, reason.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Obtiene la razón del estado DEGRADED actual.
     */
    fun getDegradedReason(): DegradedReason {
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
    fun clearDegraded() {
        prefs.edit()
            .putString(KEY_CURRENT_STATE, ServiceState.RUNNING.name)
            .putString(KEY_DEGRADED_REASON, DegradedReason.NONE.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
}
