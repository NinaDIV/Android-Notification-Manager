package com.dynamictecnologies.notificationmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevicePairingViewModel @Inject constructor(
    private val scanBluetoothDevicesUseCase: ScanBluetoothDevicesUseCase,
    private val pairDeviceUseCase: PairDeviceWithTokenUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val pairingRepository: DevicePairingRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {
    
    // Estados UI
    val bluetoothDevices: StateFlow<List<BluetoothDeviceScanner.ScannedDevice>> = 
        scanBluetoothDevicesUseCase.devices.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val isScanning: StateFlow<Boolean> = 
        scanBluetoothDevicesUseCase.isScanning.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    val currentPairing: StateFlow<DevicePairing?> = 
        pairingRepository.getCurrentPairing().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    private val _showTokenDialog = MutableStateFlow(false)
    val showTokenDialog: StateFlow<Boolean> = _showTokenDialog.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<BluetoothDeviceScanner.ScannedDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDeviceScanner.ScannedDevice?> = _selectedDevice.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    /**
     * Inicia escaneo de dispositivos Bluetooth
     */
    fun startBluetoothScan() {
        scanBluetoothDevicesUseCase.startScan().onFailure { error ->
            _pairingState.value = PairingState.Error(
                error.message ?: "Error iniciando escaneo Bluetooth"
            )
        }
    }
    
    /**
     * Detiene escaneo
     */
    fun stopBluetoothScan() {
        scanBluetoothDevicesUseCase.stopScan()
    }
    
    /**
     * Muestra dialog para ingresar token
     */
    fun showTokenDialog(device: BluetoothDeviceScanner.ScannedDevice) {
        _selectedDevice.value = device
        _showTokenDialog.value = true
        stopBluetoothScan()  // Detener escaneo al seleccionar dispositivo
    }
    
    /**
     * Cierra dialog de token
     */
    fun dismissTokenDialog() {
        _showTokenDialog.value = false
        _selectedDevice.value = null
        _pairingState.value = PairingState.Idle
    }
    
    /**
     * Vincula dispositivo con token
     */
    fun pairDevice(token: String) {
        val device = _selectedDevice.value ?: return
        
        _pairingState.value = PairingState.Pairing
        
        viewModelScope.launch {
            pairDeviceUseCase(
                bluetoothName = device.name,
                bluetoothAddress = device.address,
                token = token
            ).onSuccess {
                _pairingState.value = PairingState.Success
                _showTokenDialog.value = false
                _selectedDevice.value = null
            }.onFailure { error ->
                _pairingState.value = PairingState.Error(
                    error.message ?: "Error vinculando dispositivo"
                )
            }
        }
    }
    
    /**
     * Vincula dispositivo solo con token (sin Bluetooth scan)
     * Usado cuando el usuario ingresa el token manualmente desde el LCD del ESP32
     */
    fun pairDeviceWithToken(token: String) {
        _pairingState.value = PairingState.Pairing
        
        val username = getCurrentUsername()
        
        viewModelScope.launch {
            pairDeviceUseCase(
                bluetoothName = "ESP32_$token",
                bluetoothAddress = "00:00:00:00:00:00",
                token = token,
                username = username
            ).onSuccess {
                _pairingState.value = PairingState.Success
            }.onFailure { error ->
                _pairingState.value = PairingState.Error(
                    error.message ?: "Error vinculando dispositivo"
                )
            }
        }
    }
    

    /**
     * Desvincula dispositivo actual
     */
    fun unpairDevice() {
        _pairingState.value = PairingState.Pairing
        
        val username = getCurrentUsername()
        
        viewModelScope.launch {
            unpairDeviceUseCase(username).onSuccess {
                _pairingState.value = PairingState.Success
            }.onFailure { error ->
                _pairingState.value = PairingState.Error(
                    error.message ?: "Error desvinculando dispositivo"
                )
            }
        }
    }
    
    /**
     * Reinicia estado de pairing
     */
    fun resetPairingState() {
        _pairingState.value = PairingState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        stopBluetoothScan()
    }
    
    /**
     * Obtiene el username actual del usuario autenticado en Firebase.
     * Prioriza displayName, luego email (sin dominio), y fallback a "Usuario".
     * Limita a 16 caracteres para compatibilidad con LCD del ESP32.
     */
    private fun getCurrentUsername(): String {
        val currentUser = firebaseAuth.currentUser
        return when {
            !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName?.take(16) ?: "Usuario"
            !currentUser?.email.isNullOrBlank() -> currentUser?.email?.substringBefore("@")?.take(16) ?: "Usuario"
            else -> "Usuario"
        }
    }
    
    /**
     * Estados de la interfaz de pairing
     */
    sealed class PairingState {
        object Idle : PairingState()
        object Pairing : PairingState()
        object Success : PairingState()
        data class Error(val message: String) : PairingState()
    }
}
