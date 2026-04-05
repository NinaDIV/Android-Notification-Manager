package com.dynamictecnologies.notificationmanager.util

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject

@HiltAndroidApp
class NotificationManagerApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        try {
            // Inicializar Firebase
            FirebaseApp.initializeApp(this)

            // Configurar Firebase Database
            FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }

            Log.d("NotificationManagerApp", "Firebase inicializado correctamente")
        } catch (e: Exception) {
            Log.e("NotificationManagerApp", "Error inicializando Firebase: ${e.message}")
        }
    }
}