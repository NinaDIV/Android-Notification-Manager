package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttDeviceLinkManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttDeviceScanner
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttMessageHandler
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttNotificationSender

import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttSubscriptionManager
import com.dynamictecnologies.notificationmanager.data.repository.DevicePairingRepositoryImpl
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.device.PairDeviceWithTokenUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.ScanBluetoothDevicesUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.SendNotificationToDeviceUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.device.UnpairDeviceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para componentes Bluetooth y MQTT.
 * 
 * Provee singletons para mantener una única conexión MQTT activa.
 */
@Module
@InstallIn(SingletonComponent::class)
object BluetoothMqttHiltModule {
    
    // ========================================
    // BLUETOOTH
    // ========================================
    
    @Provides
    fun provideBluetoothDeviceScanner(@ApplicationContext context: Context): BluetoothDeviceScanner =
        BluetoothDeviceScanner(context)
    
    // ========================================
    // MQTT (SINGLETONS - una sola conexión)
    // ========================================
    
    @Provides
    @Singleton
    fun provideMqttConnectionManager(@ApplicationContext context: Context): MqttConnectionManager =
        MqttConnectionManager(context)
    
    @Provides
    @Singleton
    fun provideMqttNotificationSender(connectionManager: MqttConnectionManager): MqttNotificationSender =
        MqttNotificationSender(connectionManager)
    
    @Provides
    fun provideMqttDeviceScanner(connectionManager: MqttConnectionManager): MqttDeviceScanner =
        MqttDeviceScanner(connectionManager)
    
    @Provides
    fun provideMqttSubscriptionManager(connectionManager: MqttConnectionManager): MqttSubscriptionManager =
        MqttSubscriptionManager(connectionManager)
    
    @Provides
    fun provideMqttMessageHandler(@ApplicationContext context: Context): MqttMessageHandler =
        MqttMessageHandler(context)
    
    @Provides
    fun provideMqttDeviceLinkManager(
        connectionManager: MqttConnectionManager,
        subscriptionManager: MqttSubscriptionManager
    ): MqttDeviceLinkManager =
        MqttDeviceLinkManager(connectionManager, subscriptionManager)
    
    // ========================================
    // REPOSITORY
    // ========================================
    
    @Provides
    @Singleton
    fun provideDevicePairingRepository(
        @ApplicationContext context: Context
    ): DevicePairingRepository =
        DevicePairingRepositoryImpl(
            context.getSharedPreferences("device_pairing", Context.MODE_PRIVATE)
        )
    
    // ========================================
    // USE CASES
    // ========================================
    
    @Provides
    fun provideScanBluetoothDevicesUseCase(scanner: BluetoothDeviceScanner) =
        ScanBluetoothDevicesUseCase(scanner)
    
    @Provides
    fun providePairDeviceWithTokenUseCase(
        pairingRepository: DevicePairingRepository,
        connectionManager: MqttConnectionManager,
        notificationSender: MqttNotificationSender
    ) = PairDeviceWithTokenUseCase(pairingRepository, connectionManager, notificationSender)
    
    @Provides
    fun provideSendNotificationToDeviceUseCase(
        pairingRepository: DevicePairingRepository,
        mqttSender: MqttNotificationSender
    ) = SendNotificationToDeviceUseCase(pairingRepository, mqttSender)
    
    @Provides
    fun provideUnpairDeviceUseCase(
        pairingRepository: DevicePairingRepository,
        connectionManager: MqttConnectionManager,
        notificationSender: MqttNotificationSender
    ) = UnpairDeviceUseCase(pairingRepository, connectionManager, notificationSender)
}
