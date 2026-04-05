package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests unitarios para ServiceStateManager.
 * 
 * Verifica:
 * - FSM de 3 estados (RUNNING/STOPPED/DISABLED)
 * - Persistencia de estados
 * - Lógica de mostrar notificaciones
 * - Reset de contador
 * - Reset on app open
 */
@RunWith(RobolectricTestRunner::class)
class ServiceStateManagerTest {
    
    private lateinit var context: Context
    private lateinit var serviceStateManager: ServiceStateManager
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        
        serviceStateManager = ServiceStateManager(prefs)
    }
    
    @After
    fun tearDown() {
        // Limpiar después de cada test
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @Test
    fun test_01_defaultStateIsRunning() {
        // Cuando no hay estado guardado, debe retornar RUNNING
        val state = serviceStateManager.getCurrentState()
        
        assertEquals(
            "Estado por defecto debe ser RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
    }
    
    @Test
    fun test_02_setStateRunning() {
        // Establecer estado RUNNING
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.RUNNING, state)
    }
    
    @Test
    fun test_03_setStateStopped() {
        // Establecer estado STOPPED
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.STOPPED, state)
    }
    
    @Test
    fun test_04_setStateDisabled() {
        // Establecer estado DISABLED
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.DISABLED, state)
    }
    
    @Test
    fun test_05_stateTransition_runningToStopped() {
        // Transición RUNNING → STOPPED
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.STOPPED, state)
    }
    
    @Test
    fun test_06_stateTransition_stoppedToDisabled() {
        // Transición STOPPED → DISABLED
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.DISABLED, state)
    }
    
    @Test
    fun test_07_stateTransition_stoppedToRunning() {
        // Transición STOPPED → RUNNING (reinicio)
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val state = serviceStateManager.getCurrentState()
        assertEquals(ServiceStateManager.ServiceState.RUNNING, state)
    }
    
    @Test
    fun test_08_canShowStoppedNotification_whenRunningAndNotShown() {
        // Estado RUNNING + no mostrada = true
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        val canShow = serviceStateManager.canShowStoppedNotification()
        
        assertTrue(
            "Debe poder mostrar notificación STOPPED cuando estado es RUNNING y no se ha mostrado",
            canShow
        )
    }
    
    @Test
    fun test_09_canShowStoppedNotification_whenRunningButAlreadyShown() {
        // Estado RUNNING + ya mostrada = false
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.markStoppedNotificationShown()
        
        val canShow = serviceStateManager.canShowStoppedNotification()
        
        assertFalse(
            "NO debe mostrar notificación STOPPED si ya fue mostrada en esta sesión",
            canShow
        )
    }
    
    @Test
    fun test_10_canShowStoppedNotification_whenStopped() {
        // Estado STOPPED = false
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        
        val canShow = serviceStateManager.canShowStoppedNotification()
        
        assertFalse(
            "NO debe mostrar notificación STOPPED cuando estado ya es STOPPED",
            canShow
        )
    }
    
    @Test
    fun test_11_canShowStoppedNotification_whenDisabled() {
        // Estado DISABLED = false
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        
        val canShow = serviceStateManager.canShowStoppedNotification()
        
        assertFalse(
            "NO debe mostrar notificación STOPPED cuando estado es DISABLED",
            canShow
        )
    }
    
    @Test
    fun test_12_markStoppedNotificationShown() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        // Antes de marcar
        assertTrue(serviceStateManager.canShowStoppedNotification())
        
        // Marcar como mostrada
        serviceStateManager.markStoppedNotificationShown()
        
        // Después de marcar
        assertFalse(serviceStateManager.canShowStoppedNotification())
    }
    
    @Test
    fun test_13_stoppedCounterIncrement() {
        // Contador debe incrementar
        assertEquals(0, serviceStateManager.getStoppedCount())
        
        serviceStateManager.markStoppedNotificationShown()
        assertEquals(1, serviceStateManager.getStoppedCount())
        
        serviceStateManager.resetStoppedCounter()
        serviceStateManager.markStoppedNotificationShown()
        assertEquals(2, serviceStateManager.getStoppedCount())
    }
    
    @Test
    fun test_14_resetStoppedCounter() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.markStoppedNotificationShown()
        
        // Después de marcar, no puede mostrar
        assertFalse(serviceStateManager.canShowStoppedNotification())
        
        // Resetear contador
        serviceStateManager.resetStoppedCounter()
        
        // Ahora puede mostrar otra vez
        assertTrue(serviceStateManager.canShowStoppedNotification())
    }
    
    @Test
    fun test_15_resetOnAppOpen_fromDisabledToRunning() {
        // Simular que usuario eligió "Entendido" (DISABLED)
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, serviceStateManager.getCurrentState())
        
        // Usuario abre app de nuevo
        serviceStateManager.resetOnAppOpen()
        
        // Estado debe cambiar a RUNNING
        assertEquals(
            "resetOnAppOpen() debe cambiar DISABLED → RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            serviceStateManager.getCurrentState()
        )
    }
    
    @Test
    fun test_16_resetOnAppOpen_fromStoppedStaysStopped() {
        // Si estado es STOPPED, debe mantenerse
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        serviceStateManager.resetOnAppOpen()
        
        // STOPPED no cambia automáticamente
        assertEquals(
            "STOPPED debe mantenerse (no es DISABLED)",
            ServiceStateManager.ServiceState.STOPPED,
            serviceStateManager.getCurrentState()
        )
    }
    
    @Test
    fun test_17_resetOnAppOpen_resetsCounter() {
        // Marcar notificación como mostrada
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.markStoppedNotificationShown()
        assertFalse(serviceStateManager.canShowStoppedNotification())
        
        // resetOnAppOpen debe resetear contador
        serviceStateManager.resetOnAppOpen()
        
        // Ahora puede mostrar otra vez
        assertTrue(
            "resetOnAppOpen() debe resetear contador de notificaciones",
            serviceStateManager.canShowStoppedNotification()
        )
    }
    
    @Test
    fun test_18_getTimeSinceLastStateChange() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        // Wait a bit
        Thread.sleep(100)
        
        val timeSince = serviceStateManager.getTimeSinceLastStateChange()
        
        assertTrue(
            "Debe haber pasado al menos 100ms desde último cambio",
            timeSince >= 100
        )
    }
    
    @Test
    fun test_19_statePersistence() {
        // Establecer estado
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        
        // Obtener nuevo contexto y prefs (simula reinicio de app)
        val newPrefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        
        val newManager = ServiceStateManager(newPrefs)
        
        // Estado debe persistir
        assertEquals(
            "Estado debe persistir en SharedPreferences",
            ServiceStateManager.ServiceState.STOPPED,
            newManager.getCurrentState()
        )
    }
    
    @Test
    fun test_20_completeLifecycle() {
        // Test de ciclo completo
        
        // 1. App inicia (RUNNING)
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        assertTrue(serviceStateManager.canShowStoppedNotification())
        
        // 2. Servicio muere, mostrar STOPPED
        serviceStateManager.markStoppedNotificationShown()
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        assertFalse(serviceStateManager.canShowStoppedNotification())
        assertEquals(1, serviceStateManager.getStoppedCount())
        
        // 3. Usuario presiona "Reiniciar"
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.resetStoppedCounter()
        assertTrue(serviceStateManager.canShowStoppedNotification())
        
        // 4. Servicio muere otra vez
        serviceStateManager.markStoppedNotificationShown()
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        assertEquals(2, serviceStateManager.getStoppedCount())
        
        // 5. Usuario presiona "Entendido"
        serviceStateManager.setState(ServiceStateManager.ServiceState.DISABLED)
        assertFalse(serviceStateManager.canShowStoppedNotification())
        
        // 6. Usuario abre app de nuevo
        serviceStateManager.resetOnAppOpen()
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
        assertTrue(serviceStateManager.canShowStoppedNotification())
    }
}
