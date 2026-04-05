package com.dynamictecnologies.notificationmanager.service.util

import android.content.Context
import android.util.Log
import com.dynamictecnologies.notificationmanager.di.ServicePrefs
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detector de muerte inesperada del servicio.
 */
@Singleton
class ServiceDeathDetector @Inject constructor(
    private val serviceStateManager: ServiceStateManager,
    private val serviceNotificationManager: ServiceNotificationManager,
    @ServicePrefs private val servicePrefs: android.content.SharedPreferences
) {
    
    companion object {
        private const val TAG = "ServiceDeathDetector"
        private const val KEY_LAST_KNOWN_STATE = "last_known_running_state"
        private const val KEY_LAST_HEARTBEAT = "service_last_heartbeat"
        private const val KEY_SHOULD_BE_RUNNING = "service_should_be_running"
        private const val HEARTBEAT_TIMEOUT = 12 * 60 * 1000L // 12 minutos
    }
    
    fun wasServiceKilledUnexpectedly(): Boolean {
        val currentState = serviceStateManager.getCurrentState()
        val shouldBeRunning = servicePrefs.getBoolean(KEY_SHOULD_BE_RUNNING, false)
        val lastHeartbeat = servicePrefs.getLong(KEY_LAST_HEARTBEAT, 0)
        
        if (currentState == ServiceStateManager.ServiceState.DISABLED) {
            return false
        }
        
        if (shouldBeRunning && currentState == ServiceStateManager.ServiceState.RUNNING) {
            val timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat
            val heartbeatTimeout = 15 * 60 * 1000L // 15 minutos
            
            if (lastHeartbeat > 0 && timeSinceHeartbeat > heartbeatTimeout) {
                return true
            }
            if (lastHeartbeat == 0L) {
                return true
            }
        }
        return false
    }
    
    fun handleDeathOnAppStart() {
        if (wasServiceKilledUnexpectedly()) {
            serviceNotificationManager.showStoppedNotification(ServiceNotificationManager.StopReason.PERMISSION_REVOKED)
            serviceStateManager.markStoppedNotificationShown()
            serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
            recordDeathEvent()
        }
    }
    
    fun markServiceAsRunning() {
        servicePrefs.edit()
            .putBoolean(KEY_LAST_KNOWN_STATE, true)
            .putLong("last_start_time", System.currentTimeMillis())
            .apply()
    }
    
    private fun recordDeathEvent() {
        val currentCount = servicePrefs.getInt("death_on_start_count", 0)
        servicePrefs.edit()
            .putInt("death_on_start_count", currentCount + 1)
            .putLong("last_death_on_start", System.currentTimeMillis())
            .apply()
    }
}
