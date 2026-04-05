package com.dynamictecnologies.notificationmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver.PendingResult
import kotlinx.coroutines.cancel

/**
 * Receiver para detectar cambios en el estado de la red.
 * 
 * Cuando la red vuelve a estar disponible (por ejemplo, al salir del 
 * modo Doze o al cambiar de WiFi a datos móviles), intenta reconectar MQTT.
 * 
 * Esto es crucial para mantener la conexión MQTT activa porque:
 * - Doze mode suspende la red periódicamente
 * - Cambios de red (WiFi/Mobile) pueden desconectar MQTT
 * - El usuario puede apagar/encender WiFi manualmente
 */
@AndroidEntryPoint
class NetworkStateReceiver : BroadcastReceiver() {
    
    @Inject lateinit var mqttManager: MqttConnectionManager
    
    companion object {
        private const val TAG = "NetworkStateReceiver"
        private var lastKnownConnected = false
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) {
            return
        }
        
        val isConnected = isNetworkAvailable(context)
        Log.d(TAG, "Cambio de conectividad detectado: connected=$isConnected")
        
        // Solo actuar si pasamos de desconectado a conectado
        if (isConnected && !lastKnownConnected) {
            Log.d(TAG, "Red recuperada - intentando reconectar MQTT")
            val pendingResult = goAsync()
            tryReconnectMqtt(context, pendingResult)
        }
        
        lastKnownConnected = isConnected
    }
    
    /**
     * Verifica si hay conectividad de red disponible.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
    
    /**
     * Intenta reconectar MQTT de forma asíncrona respaldado por el BroadcastReceiver.
     */
    private fun tryReconnectMqtt(context: Context, pendingResult: PendingResult) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT desconectado, intentando reconectar...")
                    val result = mqttManager.connect()
                    
                    if (result.isSuccess) {
                        Log.d(TAG, "MQTT reconectado exitosamente después de cambio de red")
                    } else {
                        Log.w(TAG, "Fallo reconexión MQTT: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d(TAG, "MQTT ya está conectado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en reconexión MQTT: ${e.message}", e)
            } finally {
                scope.cancel()
                pendingResult.finish()
            }
        }
    }
}
