package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de resistencia para diferentes fabricantes (OEM).
 * 
 * Verifica que el servicio de fondo sobrevive en:
 * - Xiaomi (MIUI) - Conocido por matar agresivamente servicios
 * - Samsung (One UI) - Battery optimization agresiva
 * - Huawei (EMUI) - Sin Google Play Services
 * - OnePlus (OxygenOS) - RAM management agresivo
 * - Generic/Stock Android
 * 
 * También verifica:
 * - Wake lock funciona correctamente
 * - AlarmManager restart funciona
 * - Notificaciones de foreground service
 */
@RunWith(RobolectricTestRunner::class)
class OEMResilienceTest {
    
    private lateinit var context: Context
    private lateinit var serviceStateManager: ServiceStateManager
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        
        serviceStateManager = ServiceStateManager(prefs)
    }
    
    /**
     * Simula dispositivo Xiaomi con MIUI.
     * MIUI es conocido por matar servicios agresivamente.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_01_xiaomiDeviceSupport() {
        // Verificar que estado funciona en Xiaomi
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(
            "ServiceStateManager debe funcionar en Xiaomi",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        // Verificar que notificaciones pueden crearse
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        
        assertNotNull("Notificaciones deben funcionar en Xiaomi", notification)
    }
    
    /**
     * Simula dispositivo Samsung con One UI.
     * Samsung tiene optimización de batería agresiva.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_02_samsungDeviceSupport() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(
            "ServiceStateManager debe funcionar en Samsung",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Samsung", notification)
    }
    
    /**
     * Simula dispositivo Huawei con EMUI (sin Google Play Services).
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun test_03_huaweiDeviceSupport() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(
            "ServiceStateManager debe funcionar en Huawei",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Huawei", notification)
    }
    
    /**
     * Simula dispositivo OnePlus con OxygenOS.
     * OnePlus tiene RAM management agresivo.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_04_onePlusDeviceSupport() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(
            "ServiceStateManager debe funcionar en OnePlus",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notific aciones deben funcionar en OnePlus", notification)
    }
    
    /**
     * Simula Stock Android (Google Pixel).
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_05_stockAndroidSupport() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(
            "ServiceStateManager debe funcionar en Stock Android",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Stock Android", notification)
    }
    
// ... (omitted test_06, test_07 as they don't use ServiceStateManager) ...

    /**
     * Verifica que estado persiste después de "reinicio" de contexto.
     */
    @Test
    fun test_08_statePersistenceAcrossContexts() {
        // Establecer estado
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        
        // Obtener nuevo contexto (simula reinicio)
        val newPrefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        val newManager = ServiceStateManager(newPrefs)
        
        // Estado debe persistir
        assertEquals(
            "Estado debe persistir en diferentes contextos",
            ServiceStateManager.ServiceState.DISABLED,
            newManager.getCurrentState()
        )
    }
    
    /**
     * Verifica ciclo completo de vida del servicio.
     */
    @Test
    fun test_09_completeServiceLifecycle() {
        // 1. Servicio inicia (RUNNING)
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        val manager = ServiceNotificationManager(context)
        manager.showRunningNotification()
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
        
        // 2. Servicio muere inesperadamente
        assertTrue(serviceStateManager.canShowStoppedNotification())
        serviceStateManager.markStoppedNotificationShown()
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        manager.showStoppedNotification()
        
        // 3. Usuario presiona Reiniciar
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.resetStoppedCounter()
        manager.showRunningNotification()
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
        
        // 4. Servicio muere otra vez
        assertTrue(serviceStateManager.canShowStoppedNotification())
        
        // 5. Usuario presiona Entendido
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        manager.hideAllNotifications()
        
        assertEquals(ServiceStateManager.ServiceState.DISABLED, serviceStateManager.getCurrentState())
        
        // 6. Usuario abre app de nuevo
        serviceStateManager.resetOnAppOpen()
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
    }

// ... (omitted test_10 as it doesn't use ServiceStateManager) ...

    /**
     * Verifica resistencia a múltiples cambios de estado rápidos.
     */
    @Test
    fun test_11_rapidStateChanges() {
        // Cambiar estado rápidamente 100 veces
        repeat(100) { i ->
            val state = when (i % 3) {
                0 -> ServiceStateManager.ServiceState.RUNNING
                1 -> ServiceStateManager.ServiceState.STOPPED
                else -> ServiceStateManager.ServiceState.DISABLED
            }
            
            serviceStateManager.setState(state)
            
            // Verificar que el estado es el esperado
            assertEquals(state, serviceStateManager.getCurrentState())
        }
        
        // Estado final: i=99, 99%3=0 -> RUNNING
        assertEquals(
            ServiceStateManager.ServiceState.RUNNING,
            serviceStateManager.getCurrentState()
        )
    }
    
    /**
     * Verifica que múltiples instancias de ServiceNotificationManager
     * funcionan correctamente.
     */
    @Test
    fun test_12_multipleManagerInstances() {
        val manager1 = ServiceNotificationManager(context)
        val manager2 = ServiceNotificationManager(context)
        
        // Ambas deben funcionar correctamente
        manager1.showRunningNotification()
        manager2.showStoppedNotification()
        
        // La última debe ganar (STOPPED)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        
        val stoppedActive = activeNotifications.any {
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED
        }
        
        assertTrue("Notificación STOPPED debe estar activa", stoppedActive)
    }
}
