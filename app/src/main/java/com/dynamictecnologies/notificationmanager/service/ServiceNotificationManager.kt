package com.dynamictecnologies.notificationmanager.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor de notificaciones dinámicas del servicio.
 * Inyectable via Hilt.
 */
@Singleton
class ServiceNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CHANNEL_ID_RUNNING = "service_running_channel"
        private const val CHANNEL_ID_STOPPED = "service_stopped_channel"
        const val NOTIFICATION_ID_RUNNING = 100
        const val NOTIFICATION_ID_STOPPED = 200
    }

    enum class StopReason {
        UNEXPECTED, USER_STOP, ERROR, PERMISSION_REVOKED, POWER_RESTRICTED
    }
    
    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    fun showRunningNotification(): Notification {
        val notification = createRunningNotification()
        notificationManager.notify(NOTIFICATION_ID_RUNNING, notification)
        notificationManager.cancel(NOTIFICATION_ID_STOPPED)
        return notification
    }
    
    fun showStoppedNotification(reason: StopReason = StopReason.UNEXPECTED) {
        val notification = createStoppedNotification(reason)
        notificationManager.notify(NOTIFICATION_ID_STOPPED, notification)
        notificationManager.cancel(NOTIFICATION_ID_RUNNING)
    }
    
    fun hideAllNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_RUNNING)
        notificationManager.cancel(NOTIFICATION_ID_STOPPED)
    }
    
    private fun createRunningNotification(): Notification {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RUNNING)
            .setContentTitle("Gestor de Notificaciones")
            .setContentText("Corriendo en segundo plano")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setColor(Color.parseColor("#06402B"))
            .setColorized(true)
            .addAction(R.drawable.ic_notification, "DETENER", stopPendingIntent)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        return builder.build()
    }
    
    private fun createStoppedNotification(reason: StopReason): Notification {
        val title: String
        val contentText: String
        val bigText: String
        val color: Int
        
        when (reason) {
            StopReason.UNEXPECTED -> {
                title = "Servicio Detenido"
                contentText = "El servicio se detuvo inesperadamente"
                bigText = "El servicio de notificaciones se detuvo inesperadamente.\n\nPresiona Reiniciar para continuar o Entendido para desactivar."
                color = Color.RED
            }
            StopReason.USER_STOP -> {
                title = "Servicio Detenido"
                contentText = "El servicio fue detenido por el usuario"
                bigText = "Has detenido el servicio de notificaciones.\n\nPresiona Reiniciar para volver a activarlo."
                color = Color.parseColor("#FFA500")
            }
            StopReason.ERROR -> {
                title = "Error en el Servicio"
                contentText = "El servicio se detuvo debido a un error"
                bigText = "El servicio de notificaciones encontró un error y se detuvo.\n\nPresiona Reiniciar para intentar de nuevo o Entendido para desactivar."
                color = Color.RED
            }
            StopReason.PERMISSION_REVOKED -> {
                title = "Permiso Revocado"
                contentText = "El permiso de notificaciones fue revocado"
                bigText = "El permiso de acceso a notificaciones fue revocado.\n\nNecesitas otorgarlo nuevamente."
                color = Color.parseColor("#FFC107")
            }
            StopReason.POWER_RESTRICTED -> {
                title = "Modo Ahorro Activo"
                contentText = "El dispositivo está en modo ahorro de batería"
                bigText = "El modo ahorro de batería o Doze está activo.\n\nEl monitoreo puede estar limitado hasta que se desactive."
                color = Color.parseColor("#FFC107")
            }
        }

        val restartIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_RESTART_SERVICE
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            context, 2, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val acknowledgeIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_ACKNOWLEDGE
        }
        val acknowledgePendingIntent = PendingIntent.getBroadcast(
            context, 3, acknowledgeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STOPPED)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(false)
            .setColor(color)
            .setColorized(true)
            .addAction(R.drawable.ic_notification, "Reiniciar", restartPendingIntent)

        if (reason == StopReason.UNEXPECTED || reason == StopReason.ERROR || reason == StopReason.PERMISSION_REVOKED) {
            builder.addAction(R.drawable.ic_notification, "Entendido", acknowledgePendingIntent)
        }
        
        return builder.build()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val runningChannel = NotificationChannel(
                CHANNEL_ID_RUNNING, "Servicio Activo", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificación cuando el servicio está corriendo"
                setShowBadge(false)
            }
            val stoppedChannel = NotificationChannel(
                CHANNEL_ID_STOPPED, "Servicio Detenido", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerta cuando el servicio se detiene inesperadamente"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(runningChannel)
            notificationManager.createNotificationChannel(stoppedChannel)
        }
    }
}
