package com.dynamictecnologies.notificationmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppNavigation
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import com.dynamictecnologies.notificationmanager.service.util.ServiceDeathDetector

import com.dynamictecnologies.notificationmanager.presentation.core.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionHelper
import com.dynamictecnologies.notificationmanager.util.PermissionDialogCoordinator
import com.dynamictecnologies.notificationmanager.presentation.core.dialog.PermissionDialogContent
import com.dynamictecnologies.notificationmanager.viewmodel.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.dynamictecnologies.notificationmanager.worker.ServiceHealthCheckWorker
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Estado Compose para el diálogo de permisos
    private var showPermissionDialogState = mutableStateOf(false)
    
    private lateinit var permissionCoordinator: PermissionDialogCoordinator

    // ViewModels inyectados por Hilt (reemplaza factories manuales)
    private val authViewModel: AuthViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val appListViewModel: AppListViewModel by viewModels()
    private val devicePairingViewModel: DevicePairingViewModel by viewModels()
    
    // Repositorios inyectados por Hilt
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var serviceStateManager: ServiceStateManager
    @Inject lateinit var serviceDeathDetector: ServiceDeathDetector
    
    // Permission launcher para POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "[OK] Permiso POST_NOTIFICATIONS otorgado")
            // Iniciar servicio después de obtener permiso
            startNotificationService()
        } else {
            Log.w("MainActivity", "[WARN] Permiso POST_NOTIFICATIONS denegado")
            // Mostrar diálogo explicativo a través del coordinador
            permissionCoordinator.showNotificationPermissionRationale(notificationPermissionLauncher)
        }
    }
    
    // Permission launcher para permisos de Bluetooth
    private val bluetoothPermissionLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d("MainActivity", "Permisos Bluetooth otorgados")
            checkAndEnableBluetooth()
        } else {
            Log.w("MainActivity", "Algunos permisos Bluetooth fueron denegados")
            permissionCoordinator.showBluetoothPermissionRationale(bluetoothPermissionLauncher)
        }
    }

    // Launcher para habilitar Bluetooth
    private val enableBluetoothLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Bluetooth habilitado por el usuario")
            devicePairingViewModel.startBluetoothScan()
        } else {
            Log.w("MainActivity", "Usuario rechazó habilitar Bluetooth")
            permissionCoordinator.showBluetoothEnableRationale(enableBluetoothLauncher)
        }
    }
    
    /**
     * Solicita permisos de Bluetooth.
     * Una vez otorgados, verifica si Bluetooth está encendido.
     */
    private fun requestBluetoothPermissions() {
        if (PermissionHelper.hasBluetoothPermissions(this)) {
            Log.d("MainActivity", "Permisos Bluetooth ya otorgados")
            checkAndEnableBluetooth()
        } else {
            Log.d("MainActivity", "Solicitando permisos Bluetooth...")
            val permissions = PermissionHelper.getRequiredBluetoothPermissions()
            bluetoothPermissionLauncher.launch(permissions)
        }
    }
    
    /**
     * Verifica si Bluetooth está encendido y lo habilita si es necesario.
     */
    private fun checkAndEnableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            permissionCoordinator.showBluetoothNotSupportedDialog()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.d("MainActivity", "Bluetooth apagado, solicitando habilitarlo...")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            Log.d("MainActivity", "Bluetooth ya está encendido")
            devicePairingViewModel.startBluetoothScan()
        }
    }
    
    // BroadcastReceiver para manejar solicitudes de permisos
    private val permissionBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS" -> {
                    Log.d("MainActivity", "[INFO] Recibida solicitud de mostrar permisos")
                    showPermissionDialog()
                }
                "com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG" -> {
                    Log.d("MainActivity", "[INFO] Recibida solicitud de mostrar diálogo de permisos")
                    showPermissionDialog()
                }
                "com.dynamictecnologies.notificationmanager.PERMISSIONS_GRANTED" -> {
                    Log.d("MainActivity", "Permisos otorgados - notificando al repositorio")
                    notifyPermissionGranted()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionCoordinator = PermissionDialogCoordinator(this)
        
        // IMPORTANTE: Detectar si el servicio murió mientras la app estaba cerrada
        // Esto debe ir ANTES de resetOnAppOpen para poder verificar el estado previo
        serviceDeathDetector.handleDeathOnAppStart()
        
        // IMPORTANTE: Resetear estado del servicio cuando usuario abre la app
        serviceStateManager.resetOnAppOpen()
        
        // Registrar el receiver para permisos
        registerPermissionReceiver()

        // Firebase ya inicializado en NotificationManagerApp.kt
        
        // Pedir permiso POST_NOTIFICATIONS antes de iniciar servicio (Android 13+)
        requestNotificationPermissionAndStartService()
        
        // Nota: Permisos Bluetooth se solicitan SOLO cuando el usuario 
        // presiona el botón "Conectar" en la pantalla principal

        setContent {
            NotificationManagerTheme {
                // Contenido principal de la aplicación
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authViewModel = authViewModel,
                        appListViewModel = appListViewModel,
                        userViewModel = userViewModel,
                        devicePairingViewModel = devicePairingViewModel,
                        requestBluetoothPermissions = { requestBluetoothPermissions() }
                    )
                    
                    // Diálogo de permisos Material3 (centralizado)
                    if (showPermissionDialogState.value) {
                        PermissionDialogContent(
                            onDismiss = {
                                showPermissionDialogState.value = false
                                Log.d(TAG, "Usuario pospuso configuración de permisos")
                            },
                            onGoToSettings = {
                                showPermissionDialogState.value = false
                                PermissionHelper.openNotificationListenerSettings(this@MainActivity)
                            }
                        )
                    }
                }
            }
        }
        
        // Programar watchdog de WorkManager
        scheduleServiceHealthCheckIfNeeded()

        // Verificar permisos al iniciar (con retraso para que la UI se estabilice)
        checkPermissionsOnStartup()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - verificando permisos...")
        
        // Verificar permisos cada vez que la app vuelve al foco
        val hasNotificationListenerPermission = NotificationListenerService.isNotificationListenerEnabled(this)
        
        if (hasNotificationListenerPermission) {
            Log.d(TAG, "Permisos NotificationListener activos")
            
            // Cerrar diálogo de permisos si estaba abierto
            showPermissionDialogState.value = false
            
            notifyPermissionGranted()
            
            // Reiniciar servicio si no está corriendo
            startNotificationService()
        } else {
            // Solo mostrar diálogo si no está ya visible
            if (!showPermissionDialogState.value) {
                Log.w(TAG, "Sin permisos NotificationListener - mostrando diálogo")
                showPermissionDialog()
            }
        }
    }

    
    /**
     * Pide permiso de notificaciones POST_NOTIFICATIONS (Android 13+) e inicia servicio
     */
    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permiso ya otorgado
                    Log.d("MainActivity", "POST_NOTIFICATIONS ya otorgado")
                    startNotificationService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Mostrar explicación antes de pedir permiso
                    permissionCoordinator.showNotificationPermissionRationale(notificationPermissionLauncher)
                }
                else -> {
                    // Pedir permiso directamente
                    Log.d("MainActivity", "Pidiendo permiso POST_NOTIFICATIONS")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android < 13, no necesita permiso POST_NOTIFICATIONS
            startNotificationService()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar el receiver
        try {
            unregisterReceiver(permissionBroadcastReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error desregistrando receiver: ${e.message}")
        }
    }

    /**
     * Registra el BroadcastReceiver para permisos
     */
    private fun registerPermissionReceiver() {
    try {
        val filter = IntentFilter().apply {
            addAction("com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS")
            addAction("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
            addAction("com.dynamictecnologies.notificationmanager.PERMISSIONS_GRANTED")
        }
        // CORRECCIÓN PARA ANDROID 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                permissionBroadcastReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED  // <- REQUERIDO EN ANDROID 13+
            )
        } else {
            registerReceiver(permissionBroadcastReceiver, filter)
        }
        Log.d("MainActivity", "BroadcastReceiver de permisos registrado")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error registrando BroadcastReceiver: ${e.message}")
    }
}

    /**
     * Verifica y muestra diálogo de permisos al iniciar con retraso.
     */
    private fun checkPermissionsOnStartup() {
        // Esperar a que la UI se estabilice antes de verificar permisos
        Handler(Looper.getMainLooper()).postDelayed({
            val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(this)
            if (!hasPermissions && !showPermissionDialogState.value) {
                Log.w("MainActivity", "Permisos de notificación no otorgados al iniciar")
                showPermissionDialog()
            }
        }, 2000) // 2 segundos
    }
    
    /**
     * Programa el watchdog de WorkManager si aún no está programado.
     * Esto asegura que el monitoreo esté siempre activo.
     */
    private fun scheduleServiceHealthCheckIfNeeded() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceHealthCheckWorker>(
                15, TimeUnit.MINUTES // Cada 15 minutos (mínimo de Android)
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // IMPORTANTE: ejecutar siempre
                        .build()
                )
                .addTag("service_health_check")
                .build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "service_health_check",
                ExistingPeriodicWorkPolicy.KEEP, // No reemplazar si ya existe
                workRequest
            )
            
            Log.d(TAG, "Watchdog WorkManager verificado/programado desde MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando watchdog: ${e.message}", e)
        }
    }

    /**
     * Verificación al volver a la app (el usuario pudo haber otorgado permisos)
     */
    private fun checkPermissionsOnResume() {
        if (PermissionHelper.hasNotificationListenerPermission(this)) {
            Log.d("MainActivity", "Permisos confirmados en onResume")
            notifyPermissionGranted()
        } else {
            Log.w("MainActivity", "Sin permisos en onResume")
        }
    }

    /**
     * Muestra el diálogo de permisos
     */
    private fun showPermissionDialog() {
        // Evitar múltiples diálogos
        if (showPermissionDialogState.value) {
            Log.d("MainActivity", "Diálogo de permisos ya visible - ignorando")
            return
        }
        
        // Verificar si ya tiene permisos (usuario pudo otorgarlos)
        if (NotificationListenerService.isNotificationListenerEnabled(this)) {
            Log.d("MainActivity", "Permisos ya otorgados - no se muestra diálogo")
            return
        }
        
        if (!isFinishing && !isDestroyed) {
            Log.d("MainActivity", "Mostrando diálogo de permisos (Compose)")
            showPermissionDialogState.value = true
        } else {
            Log.w("MainActivity", "Activity terminando - no se muestra diálogo")
        }
    }

    /**
     * Notifica que los permisos fueron otorgados
     */
    private fun notifyPermissionGranted() {
        try {
            // Notificar al repositorio que rechecke permisos
            val repository = notificationRepository
            repository.recheckPermissions()

            Log.d("MainActivity", "Repositorio notificado sobre permisos otorgados")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error notificando permisos: ${e.message}")
        }
    }

    // ELIMINADO: initializeFirebase() - ya está en NotificationManagerApp.kt
    // setPersistenceEnabled() solo puede llamarse una vez

    private fun startNotificationService() {
        try {
            val serviceIntent = Intent(this, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "Servicio de notificaciones iniciado")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error iniciando servicio: ${e.message}", e)
        }
    }
}