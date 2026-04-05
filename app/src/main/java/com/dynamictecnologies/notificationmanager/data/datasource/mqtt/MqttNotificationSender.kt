package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.NotificationSender

/**
 * Sender para envío de notificaciones vía MQTT.
 * 
 * Responsabilidad única: Enviar notificaciones a dispositivos ESP32.
 * 
 * Implementa NotificationSender para inversión de dependencias.
 */
class MqttNotificationSender(
    private val connectionManager: MqttConnectionManager
) : NotificationSender {
    companion object {
        private const val TAG = "MqttNotificationSender"
        // Límite de buffer ESP32 típico (256 bytes con margen de seguridad)
        private const val MAX_PAYLOAD_SIZE = 240
    }
    
    private var currentUserId: String? = null
    private var currentUsername: String? = null
    
    /**
     * Establece el usuario actual.
     */
    override fun setCurrentUser(userId: String, username: String?) {
        this.currentUserId = userId
        this.currentUsername = username
    }
    
    /**
     * Envía una notificación a un dispositivo específico.
     * 
     * @param deviceId ID del dispositivo ESP32
     * @param notification Notificación a enviar
     * @return Result<Unit> Success si se envía correctamente
     */
    override suspend fun sendNotification(
        deviceId: String,
        notification: NotificationInfo
    ): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val topic = "esp32/device/$deviceId/notification"
            val payload = buildNotificationPayload(notification)
            
            // Retornar el resultado de publish con QoS 0 (efímero, más rápido)
            connectionManager.publish(topic, payload, qos = 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Envía una notificación directamente a un topic MQTT.
     * Usado para el nuevo flujo de pairing con tokens.
     * 
     * @param topic Topic MQTT (ej: "n/ABC12345")
     * @param notification Notificación a enviar
     * @return Result<Unit> Success si se envía correctamente
     */
    override suspend fun sendNotificationToTopic(
        topic: String,
        notification: NotificationInfo
    ): Result<Unit> {
        android.util.Log.d(TAG, "=== ENVIANDO NOTIFICACIÓN A ESP32 ===")
        android.util.Log.d(TAG, "Topic: $topic")
        android.util.Log.d(TAG, "Título: ${notification.title}")
        android.util.Log.d(TAG, "App: ${notification.appName}")
        
        return try {
            val isConnected = connectionManager.isConnected()
            android.util.Log.d(TAG, "MQTT conectado: $isConnected")
            
            if (!isConnected) {
                android.util.Log.e(TAG, "ERROR: MQTT no conectado - intentando conectar...")
                // Intentar reconectar
                val connectResult = connectionManager.connect()
                if (connectResult.isFailure) {
                    android.util.Log.e(TAG, "Falló reconexión: ${connectResult.exceptionOrNull()?.message}")
                    return Result.failure(Exception("MQTT no conectado"))
                }
                android.util.Log.d(TAG, "Reconexión exitosa")
            }
            
            val payload = buildNotificationPayload(notification)
            android.util.Log.d(TAG, "Payload: $payload")
            
            // Retornar el resultado de publish
            val result = connectionManager.publish(topic, payload, qos = 0)
            if (result.isSuccess) {
                android.util.Log.d(TAG, "[OK] Notificación enviada exitosamente a $topic")
            } else {
                android.util.Log.e(TAG, "[ERROR] Error publicando: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[ERROR] Excepción: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Envía una notificación general.
     */
    override suspend fun sendGeneralNotification(title: String, content: String): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val topic = "/notificaciones/general"
            val payload = "SYSTEM|$title|$content\n"
            
            // Retornar el resultado de publish
            connectionManager.publish(topic, payload, qos = 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Envía notificación de conexión al ESP32 para que muestre el usuario conectado.
     * 
     * @param topic Topic MQTT del dispositivo (ej: "n/ABC123")
     * @param username Nombre del usuario que se conectó
     * @return Result<Unit> Success si se envía correctamente
     */
    suspend fun sendConnectionNotification(topic: String, username: String): Result<Unit> {
        android.util.Log.d(TAG, "=== NOTIFICANDO CONEXIÓN A ESP32 ===")
        android.util.Log.d(TAG, "Topic: $topic, Usuario: $username")
        
        return try {
            val isConnected = connectionManager.isConnected()
            if (!isConnected) {
                android.util.Log.e(TAG, "MQTT no conectado para enviar notificación de conexión")
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val payload = "CONEXION|${username.take(16)}\n"
            
            android.util.Log.d(TAG, "Payload conexión: $payload")
            
            val result = connectionManager.publish(topic, payload, qos = 0)
            if (result.isSuccess) {
                android.util.Log.d(TAG, "[OK] Notificación de conexión enviada a $topic")
            } else {
                android.util.Log.e(TAG, "[ERROR] Error enviando notificación de conexión")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[ERROR] Excepción: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Envía notificación de desconexión al ESP32 para que muestre desvinculación.
     * 
     * @param topic Topic MQTT del dispositivo (ej: "n/ABC123")
     * @param username Nombre del usuario que se desconectó
     * @return Result<Unit> Success si se envía correctamente
     */
    suspend fun sendDisconnectNotification(topic: String, username: String): Result<Unit> {
        android.util.Log.d(TAG, "=== NOTIFICANDO DESCONEXIÓN A ESP32 ===")
        android.util.Log.d(TAG, "Topic: $topic, Usuario: $username")
        
        return try {
            val isConnected = connectionManager.isConnected()
            if (!isConnected) {
                android.util.Log.e(TAG, "MQTT no conectado para enviar notificación de desconexión")
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val payload = "DESCONEXION|${username.take(16)}\n"
            
            android.util.Log.d(TAG, "Payload desconexión: $payload")
            
            val result = connectionManager.publish(topic, payload, qos = 0)
            if (result.isSuccess) {
                android.util.Log.d(TAG, "[OK] Notificación de desconexión enviada a $topic")
            } else {
                android.util.Log.e(TAG, "[ERROR] Error enviando notificación de desconexión")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[ERROR] Excepción: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Construye el payload JSON de la notificación.
     * 
     * SEGURIDAD:
     * - NO incluye userId ni username para evitar leakage de datos sensibles
     * - Limita payload a MAX_PAYLOAD_SIZE bytes para evitar buffer overflow en ESP32
     */
    private fun buildNotificationPayload(notification: NotificationInfo): String {
        // Formato crudo: AppName|Title|Content\n
        // Quitamos ~70 bytes de overhead de JSON
        // Esto facilita el parsing en la ESP32 usando .split("|") o strtok en C++
        val maxContentSize = MAX_PAYLOAD_SIZE - 4 // Los pipes y el newline
        
        val appNameMaxLen = minOf(30, maxContentSize / 4)
        val titleMaxLen = minOf(50, maxContentSize / 3)
        val contentMaxLen = minOf(120, maxContentSize / 2)
        
        // Removemos cualquier salto de linea o pipe en el contenido para no romper el formato
        val safeAppName = notification.appName.replace("|", "").replace("\n", " ")
        val safeTitle = notification.title.replace("|", "").replace("\n", " ")
        val safeContent = notification.content.replace("|", "").replace("\n", " ")
        
        val truncatedAppName = safeAppName.take(appNameMaxLen)
        val truncatedTitle = safeTitle.take(titleMaxLen)
        val truncatedContent = safeContent.take(contentMaxLen)
        
        return "$truncatedAppName|$truncatedTitle|$truncatedContent\n"
    }
}
