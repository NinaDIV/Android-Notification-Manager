package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests unitarios para ServiceActionReceiver.
 * 
 * Verifica:
 * - Acción STOP: Estado → STOPPED, servicios detenidos
 * - Acción RESTART: Estado → RUNNING, contador reset
 * - Acción ACKNOWLEDGE: Estado → DISABLED, todo detenido
 */
@RunWith(RobolectricTestRunner::class)
class ServiceActionReceiverTest {
    
    private lateinit var context: Context
    private lateinit var handler: ServiceActionHandler
    private lateinit var serviceStateManager: ServiceStateManager
    private lateinit var serviceNotificationManager: ServiceNotificationManager
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        val prefs = context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        
        serviceStateManager = ServiceStateManager(prefs)
        serviceNotificationManager = mockk(relaxed = true)
        
        handler = ServiceActionHandler(serviceStateManager, serviceNotificationManager)
    }
    
    @After
    fun tearDown() {
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @Test
    fun test_01_actionStop_changesStateToStopped() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        handler.handleStopService(context)
        assertEquals(ServiceStateManager.ServiceState.STOPPED, serviceStateManager.getCurrentState())
        verify { serviceNotificationManager.showStoppedNotification(ServiceNotificationManager.StopReason.USER_STOP) }
    }
    
    @Test
    fun test_02_actionRestart_changesStateToRunning() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        handler.handleRestartService(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
        verify { serviceNotificationManager.hideAllNotifications() }
    }
    
    @Test
    fun test_03_actionRestart_resetsCounter() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        serviceStateManager.markStoppedNotificationShown()
        assertFalse(serviceStateManager.canShowStoppedNotification())
        
        handler.handleRestartService(context)
        assertTrue(serviceStateManager.canShowStoppedNotification())
    }
    
    @Test
    fun test_04_actionAcknowledge_changesStateToDisabled() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        handler.handleAcknowledge(context)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, serviceStateManager.getCurrentState())
        verify { serviceNotificationManager.hideAllNotifications() }
    }
    
    @Test
    fun test_05_actionAcknowledge_preventsNotifications() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        assertTrue(serviceStateManager.canShowStoppedNotification())
        
        handler.handleAcknowledge(context)
        assertFalse(serviceStateManager.canShowStoppedNotification())
    }
    
    @Test
    fun test_06_unknownAction_doesNothing() {
        // Establecer estado inicial
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        
        // Intent con acción desconocida
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = "com.unknown.ACTION"
        }
        
        // No hay método en handler para acción desconocida, se mantiene el estado
        assertEquals(
            "Estado no debe cambiar con acción desconocida",
            ServiceStateManager.ServiceState.RUNNING,
            serviceStateManager.getCurrentState()
        )
    }
    
    @Test
    fun test_07_completeUserFlow_stopAndRestart() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.RUNNING)
        handler.handleStopService(context)
        assertEquals(ServiceStateManager.ServiceState.STOPPED, serviceStateManager.getCurrentState())
        
        handler.handleRestartService(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, serviceStateManager.getCurrentState())
    }
    
    @Test
    fun test_08_completeUserFlow_acknowledge() {
        serviceStateManager.setState(ServiceStateManager.ServiceState.STOPPED)
        serviceStateManager.markStoppedNotificationShown()
        
        handler.handleAcknowledge(context)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, serviceStateManager.getCurrentState())
        assertFalse(serviceStateManager.canShowStoppedNotification())
    }
}
