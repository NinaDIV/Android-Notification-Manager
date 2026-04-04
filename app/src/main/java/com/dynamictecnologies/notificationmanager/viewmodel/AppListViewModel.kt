package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetInstalledAppsUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.SaveSelectedAppUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val saveSelectedAppUseCase: SaveSelectedAppUseCase,
    private val getSelectedAppUseCase: GetSelectedAppUseCase,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    
    private val TAG = "AppListViewModel"
    private var notificationJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _showAppList = MutableStateFlow(false)
    val showAppList = _showAppList.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications = _notifications.asStateFlow()

    init {
        Log.d(TAG, "ViewModel inicializado con Clean Architecture")
        viewModelScope.launch {
            loadInstalledApps()
            restoreLastSelectedApp()
        }
    }

    /**
     * Carga las aplicaciones instaladas usando el Use Case.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Cargando apps instaladas...")

                getInstalledAppsUseCase()
                    .onSuccess { installedApps ->
                        Log.d(TAG, "Cargadas ${installedApps.size} apps")
                        _apps.value = installedApps
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Error cargando apps: ${error.message}")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Alterna la visibilidad del diálogo de selección de apps.
     */
    fun toggleAppList() {
        _showAppList.value = !_showAppList.value
        Log.d(TAG, "Toggle dialog: ${_showAppList.value}")
    }

    /**
     * Selecciona una aplicación y comienza a observar sus notificaciones.
     * 
     * @param app La aplicación a seleccionar o null para deseleccionar
     */
    fun selectApp(app: AppInfo?) {
        viewModelScope.launch {
            try {
                // Cancelar job anterior de notificaciones
                notificationJob?.cancel()

                Log.d(TAG, "Seleccionando app: ${app?.name}")
                _selectedApp.value = app

                app?.let { selectedApp ->
                    // Guardar selección usando Use Case
                    saveSelectedAppUseCase(selectedApp.packageName)
                    
                    Log.d(TAG, "Iniciando observación de notificaciones para: ${selectedApp.name}")
                    
                    // Limpiar notificaciones antiguas
                    // Nota: La limpieza periódica se maneja automáticamente por NotificationCleanupService
                    // No es necesario llamar manualmente aquí
                    /*launch {
                        try {
                            Log.d(TAG, "Verificando límite de notificaciones para ${selectedApp.name}")
                            // notificationRepository.cleanupOldNotifications ya no existe
                            // El cleanup es manejado automáticamente por NotificationCleanupService
                        } catch (e: Exception) {
                            Log.e(TAG, "Error limpiando notificaciones antiguas: ${e.message}")
                        }
                    }*/

                    // Observar notificaciones de la app seleccionada
                    notificationJob = launch {
                        notificationRepository.getNotifications(selectedApp.packageName)
                            .catch { e ->
                                Log.e(TAG, "Error observando notificaciones: ${e.message}")
                            }
                            .collectLatest { notificationsList ->
                                Log.d(TAG, "Actualizando lista de notificaciones: ${notificationsList.size}")
                                _notifications.value = notificationsList.sortedByDescending { it.timestamp }
                            }
                    }
                } ?: run {
                    Log.d(TAG, "No hay app seleccionada, limpiando notificaciones")
                    _notifications.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en selectApp: ${e.message}")
            }
        }
    }

    /**
     * Restaura la última aplicación seleccionada usando el Use Case.
     */
    private suspend fun restoreLastSelectedApp() {
        try {
            Log.d(TAG, "Restaurando última app seleccionada...")
            
            val app = getSelectedAppUseCase()
            
            if (app != null) {
                Log.d(TAG, "App restaurada: ${app.name}")
                selectApp(app)
            } else {
                Log.d(TAG, "No hay app seleccionada previamente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando app: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationJob?.cancel()
        Log.d(TAG, "ViewModel cleared")
    }
    
    /**
     * Limpia todos los datos del ViewModel cuando el usuario cierra sesión.
     * Cancela jobs, limpia state y borra preferencias.
     */
    fun clearData() {
        viewModelScope.launch {
            try {
                // Cancelar job de notificaciones
                notificationJob?.cancel()
                
                // Limpiar todos los StateFlows
                _selectedApp.value = null
                _notifications.value = emptyList()
                
                // Limpiar la preferencia usando Use Case
                // Pasamos string vacío para limpiar
                saveSelectedAppUseCase("")
                
                Log.d(TAG, "Datos de apps y notificaciones limpiados correctamente al cerrar sesión")
            } catch (e: Exception) {
                Log.e(TAG, "Error al limpiar datos de apps: ${e.message}")
            }
        }
    }
}