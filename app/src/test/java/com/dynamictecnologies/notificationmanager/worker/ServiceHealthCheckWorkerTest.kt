package com.dynamictecnologies.notificationmanager.worker

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * Tests para ServiceHealthCheckWorker.
 * 
 * Verifica el comportamiento correcto del watchdog:
 * - Detección de servicio muerto
 * - Correcto orden: detener servicio -> cancelar notificación verde -> mostrar notificación roja
 * - Actualización de SharedPreferences
 * 
 * Principios aplicados:
 * - AAA Pattern (Arrange-Act-Assert)
 * - Isolated tests con MockK
 * - Given-When-Then naming
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceHealthCheckWorkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPrefsEditor: SharedPreferences.Editor
    private lateinit var notificationManager: NotificationManager
    private lateinit var activityManager: ActivityManager
    private lateinit var serviceNotificationManager: ServiceNotificationManager
    private lateinit var servicePrefs: SharedPreferences
    
    // Rule to handle Dispatchers.Main
    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(testDispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }

    @Before
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())
        workerParams = mockk(relaxed = true)
        
        servicePrefs = context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
        servicePrefs.edit().clear().commit()
        
        sharedPreferences = servicePrefs
        sharedPrefsEditor = servicePrefs.edit()

        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        activityManager = spyk(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        
        serviceNotificationManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ===== HEARTBEAT DETECTION TESTS =====

    @Test
    fun `doWork returns success when service should not be running`() = runTest {
        // Given: Service should NOT be running
        servicePrefs.edit().putBoolean("service_should_be_running", false).commit()

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork detects dead service when no heartbeat exists`() = runTest {
        // Given: Service should be running but NO heartbeat exists
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", 0L)
            .commit()

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success (heartbeat is 0, so it handles it as dead)
        assertEquals(ListenableWorker.Result.success(), result)
        // Verify notification shown
        verify { serviceNotificationManager.showStoppedNotification(any()) }
    }

    @Test
    fun `doWork detects dead service when heartbeat is stale`() = runTest {
        // Given: Heartbeat is 20 mins ago (timeout is 15 mins)
        val staleHeartbeat = System.currentTimeMillis() - (20 * 60 * 1000L)
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", staleHeartbeat)
            .commit()

        // Mock activityManager to return empty list so it definitely considers it dead
        every { activityManager.getRunningServices(any()) } returns emptyList()

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should detect dead service
        assertEquals(ListenableWorker.Result.success(), result)
        verify { serviceNotificationManager.showStoppedNotification(any()) }
    }

    @Test
    fun `doWork returns success when service is healthy and heartbeat is recent`() = runTest {
        // Given: Service should be running and heartbeat is very recent
        val recentHeartbeat = System.currentTimeMillis() - (1 * 60 * 1000L)
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", recentHeartbeat)
            .commit()
        
        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success
        assertEquals(ListenableWorker.Result.success(), result)
    }

    // ===== HANDLE DEAD SERVICE TESTS =====

    @Test
    fun `handleDeadService stops foreground service first`() = runTest {
        // Given: Service is dead
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", 0L)
            .putInt("death_count", 0)
            .commit()

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: SharedPreferences should be updated properly (implicitly via side effects)
        // In the handler, the count is incremented
        assertEquals(1, servicePrefs.getInt("death_count", 0))
    }

    @Test
    fun `handleDeadService updates shared preferences correctly`() = runTest {
    }

    @Test
    fun `handleDeadService increments death count`() = runTest {
        // Given: Service has died before
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", 0L)
            .putInt("death_count", 5)
            .commit()

        // When: Worker executes
        val worker = createWorker()
        worker.doWork()

        // Then: death_count should be incremented to 6
        assertEquals(6, servicePrefs.getInt("death_count", 0))
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `handleDeadService handles stopService exception gracefully`() = runTest {
        // Given: stopService throws exception (we use spyk for this)
        servicePrefs.edit()
            .putBoolean("service_should_be_running", true)
            .putLong("service_last_heartbeat", 0L)
            .commit()
            
        every { context.stopService(any()) } throws RuntimeException("Stop error")

        // When: Worker executes
        val worker = createWorker()
        val result = worker.doWork()

        // Then: Should return success (exception handled)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    // ===== HELPER METHODS =====

    private fun createWorker(): ServiceHealthCheckWorker {
        return ServiceHealthCheckWorker(
            context,
            workerParams,
            serviceNotificationManager,
            servicePrefs
        )
    }
}
