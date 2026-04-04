package com.dynamictecnologies.notificationmanager.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio para monitorear la salud de servicios externos.
 * 
 * Monitorea:
 * - Conexión a internet
 * - Estado de Firebase (realtime check)
 * - Estado de MQTT broker
 * - Proporciona mensajes user-friendly para cada escenario
 * 
 * - Observable: Flow para reactive updates
 */
class ServiceHealthManager(private val context: Context) {
    
    private val TAG = "ServiceHealthManager"
    
    enum class ServiceStatus {
        HEALTHY,
        DEGRADED,
        DOWN,
        UNKNOWN
    }
    
    data class HealthReport(
        val internetStatus: ServiceStatus = ServiceStatus.UNKNOWN,
        val firebaseStatus: ServiceStatus = ServiceStatus.UNKNOWN,
        val mqttStatus: ServiceStatus = ServiceStatus.UNKNOWN,
        val userMessage: String = "",
        val canProceed: Boolean = false
    )
    
    private val _healthReport = MutableStateFlow(HealthReport())
    val healthReport: StateFlow<HealthReport> = _healthReport.asStateFlow()
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    init {
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                updateInternetStatus(ServiceStatus.HEALTHY)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                updateInternetStatus(ServiceStatus.DOWN)
            }
        })
    }
    
    private fun updateInternetStatus(status: ServiceStatus) {
        val current = _healthReport.value
        _healthReport.value = current.copy(
            internetStatus = status,
            userMessage = generateUserMessage(status, current.firebaseStatus, current.mqttStatus),
            canProceed = calculateCanProceed(status, current.firebaseStatus, current.mqttStatus)
        )
    }
    
    /**
     * Actualiza el estado de Firebase.
     */
    fun updateFirebaseStatus(status: ServiceStatus) {
        val current = _healthReport.value
        _healthReport.value = current.copy(
            firebaseStatus = status,
            userMessage = generateUserMessage(current.internetStatus, status, current.mqttStatus),
            canProceed = calculateCanProceed(current.internetStatus, status, current.mqttStatus)
        )
    }
    
    /**
     * Actualiza el estado de MQTT.
     */
    fun updateMqttStatus(status: ServiceStatus) {
        val current = _healthReport.value
        _healthReport.value = current.copy(
            mqttStatus = status,
            userMessage = generateUserMessage(current.internetStatus, current.firebaseStatus, status),
            canProceed = calculateCanProceed(current.internetStatus, current.firebaseStatus, status)
        )
    }
    
    /**
     * Genera mensaje user-friendly basado en el estado de los servicios.
     */
    private fun generateUserMessage(
        internetStatus: ServiceStatus,
        firebaseStatus: ServiceStatus,
        mqttStatus: ServiceStatus
    ): String {
        return when {
            // Sin internet
            internetStatus == ServiceStatus.DOWN -> 
                "Sin conexión a internet. Verifica tu conexión WiFi o datos móviles."
            
            // Firebase caído
            firebaseStatus == ServiceStatus.DOWN ->
                "El servicio de sincronización está temporalmente no disponible. " +
                "La app funcionará en modo offline. Tus datos se sincronizarán cuando el servicio se recupere."
            
            // MQTT caído
            mqttStatus == ServiceStatus.DOWN ->
                "El servicio de notificaciones en tiempo real está temporalmente no disponible. " +
                "Las notificaciones funcionarán normalmente pero sin sincronización con dispositivos ESP32."
            
            // Firebase degradado
            firebaseStatus == ServiceStatus.DEGRADED ->
                "El servicio de sincronización está experimentando problemas. " +
                "Algunas funciones pueden estar lentas."
            
            // MQTT degradado
            mqttStatus == ServiceStatus.DEGRADED ->
                "Conexión inestable con dispositivos ESP32. " +
                "Reintentando automáticamente..."
            
            // Servicios degradados pero funcionales
            firebaseStatus == ServiceStatus.DEGRADED || mqttStatus == ServiceStatus.DEGRADED ->
                "Algunos servicios están experimentando problemas. " +
                "La funcionalidad básica está disponible."
            
            // Todo healthy
            internetStatus == ServiceStatus.HEALTHY && 
            firebaseStatus == ServiceStatus.HEALTHY && 
            mqttStatus == ServiceStatus.HEALTHY ->
                "" // Sin mensaje cuando todo funciona
            
            else -> ""
        }
    }
    
    /**
     * Determina si la app puede proceder con operaciones críticas.
     */
    private fun calculateCanProceed(
        internetStatus: ServiceStatus,
        firebaseStatus: ServiceStatus,
        mqttStatus: ServiceStatus
    ): Boolean {
        // Sin internet, no podemos hacer  operaciones remotas
        if (internetStatus == ServiceStatus.DOWN) return false
        
        // Si Firebase está completamente caído, no podemos autenticar o sincronizar
        // pero permitimos modo offline
        if (firebaseStatus == ServiceStatus.DOWN) return true // Modo offline
        
        // MQTT caído no bloquea operaciones críticas
        // (solo afecta sincronización con ESP32)
        return true
    }
    
    /**
     * Verifica si hay conexión a internet.
     */
    fun hasInternetConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Obtiene el tipo de conexión actual.
     */
    fun getConnectionType(): ConnectionType {
        val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.OTHER
        }
    }
    
    enum class ConnectionType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        OTHER
    }
}
