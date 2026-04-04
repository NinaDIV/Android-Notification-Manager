package com.dynamictecnologies.notificationmanager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.worker.ServiceHealthCheckWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receptor que se activa cuando el dispositivo se inicia.
 * Inicia el servicio en primer plano y verifica los permisos.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        
        // Retraso inicial para el arranque (reducido a 10 segundos)
        private const val STARTUP_DELAY = 10 * 1000L
        
        // Retraso secundario para verificación (reducido a 2 minutos)
        private const val SECONDARY_STARTUP_DELAY = 2 * 60 * 1000L
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completado o acción recibida: ${intent.action}")
        
        // Verificar que la acción sea la correcta
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            
            // Usar goAsync() para operaciones de larga duración en BroadcastReceiver
            // Esto evita memory leaks y permite que el receiver se limpie correctamente
            val pendingResult = goAsync()
            
            // Scope limitado que se cancela al terminar
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope.launch {
                try {
                    // Programar WorkManager watchdog PRIMERO
                    delay(3000) // Esperar 3 segundos para que el sistema se estabilice
                    scheduleServiceHealthCheck(context)
                    
                    // Esperar 10 segundos para que el sistema se estabilice
                    delay(10000)
                    
                    // Verificar permisos de notificación
                    val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(context)
                    Log.d(TAG, "Estado de permisos de notificación tras arranque: $hasPermissions")
                    
                    // Iniciar el servicio en primer plano
                    val serviceIntent = Intent(context, NotificationForegroundService::class.java)
                    serviceIntent.action = NotificationForegroundService.ACTION_SCHEDULED_CHECK
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    Log.d(TAG, "Servicio iniciado tras reinicio del dispositivo")
                    
                    // Si no hay permisos, enviar broadcast para mostrar diálogo
                    if (!hasPermissions) {
                        delay(15000) // Esperar un poco más para que la UI esté lista
                        val permIntent = Intent("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
                        context.sendBroadcast(permIntent)
                        Log.d(TAG, "[INFO] Solicitando mostrar diálogo de permisos")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar servicio tras arranque: ${e.message}")
                } finally {
                    // CRÍTICO: Siempre finalizar el PendingResult para liberar recursos
                    pendingResult.finish()
                }
            }
        }
    }
    
    private suspend fun enableNotificationListenerService(context: Context) {
        try {
            // Usar toggler centralizado en lugar de código duplicado
            com.dynamictecnologies.notificationmanager.service.util.NotificationListenerToggler.toggle(
                context = context,
                delayMs = 300L
            )
            Log.d(TAG, "NotificationListenerService habilitado via Toggler")
        } catch (e: Exception) {
            Log.e(TAG, "Error habilitando NotificationListenerService: ${e.message}", e)
        }
    }
    
    private fun startForegroundService(context: Context, forceReset: Boolean = false) {
        try {
            val serviceIntent = Intent(context, NotificationForegroundService::class.java)
            
            if (forceReset) {
                serviceIntent.action = NotificationForegroundService.ACTION_FORCE_RESET
                Log.d(TAG, "Solicitando reinicio forzado de los servicios")
            } else {
                serviceIntent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "NotificationForegroundService iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando NotificationForegroundService: ${e.message}", e)
        }
    }
    
    private suspend fun performForceRestart(context: Context) {
        try {
            Log.w(TAG, "Realizando reinicio forzado completo de los servicios")
            
            // 1. Usar toggler centralizado para disable
            com.dynamictecnologies.notificationmanager.service.util.NotificationListenerToggler.disable(context)
            
            // 2. Esperar con non-blocking delay
            delay(1000)
            
            // 3. Reiniciar las preferencias
            val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("last_connection_time", 0)
                putLong("last_notification_received", 0)
                putLong("force_restart_time", System.currentTimeMillis())
                putInt("force_restart_count", prefs.getInt("force_restart_count", 0) + 1)
                apply()
            }
            
            // 4. Usar toggler centralizado para enable
            com.dynamictecnologies.notificationmanager.service.util.NotificationListenerToggler.enable(context)
            
            // 5. Iniciar el servicio de primer plano con acción de reinicio forzado
            val serviceIntent = Intent(context, NotificationForegroundService::class.java)
            serviceIntent.action = NotificationForegroundService.ACTION_FORCE_RESET
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Reinicio forzado completado")
        } catch (e: Exception) {
            Log.e(TAG, "Error en reinicio forzado: ${e.message}", e)
        }
    }
    
    private fun isServiceRunning(context: Context): Boolean {
        // Verificar si el servicio está funcionando
        val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        val lastConnectionTime = prefs.getLong("last_connection_time", 0)
        
        if (lastConnectionTime == 0L) {
            return false
        }
        
        // Si la última conexión fue hace menos de 5 minutos, asumimos que está funcionando
        return (System.currentTimeMillis() - lastConnectionTime) < 5 * 60 * 1000
    }
    
    private fun recordBootEvent(context: Context) {
        val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("last_boot_time", System.currentTimeMillis())
            putInt("boot_count", prefs.getInt("boot_count", 0) + 1)
            apply()
        }
    }
    
    /**
     * Programa el watchdog de WorkManager para monitoreo externo del servicio.
     * Este watchdog detecta cuando el servicio muere sin llamar onDestroy() (Honor/Huawei).
     */
    private fun scheduleServiceHealthCheck(context: Context) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceHealthCheckWorker>(
                15, TimeUnit.MINUTES // Cada 15 minutos (mínimo de Android)
            )
                .setInitialDelay(5, TimeUnit.MINUTES) // Esperar 5 min tras boot
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // IMPORTANTE: ejecutar siempre
                        .build()
                )
                .addTag("service_health_check")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "service_health_check",
                ExistingPeriodicWorkPolicy.KEEP, // No reemplazar si ya existe
                workRequest
            )
            
            Log.d(TAG, "WorkManager watchdog programado (intervalo: 15 min)")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando WorkManager watchdog: ${e.message}", e)
        }
    }
} 