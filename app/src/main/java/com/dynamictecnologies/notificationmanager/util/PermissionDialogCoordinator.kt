package com.dynamictecnologies.notificationmanager.util

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

/**
 * Coordinador que aísla toda la lógica UI nativa de permisos (Diálogos de Android)
 * para mantener a [MainActivity] libre de código verboso y enfocado puramente en Compose.
 */
class PermissionDialogCoordinator(private val activity: ComponentActivity) {

    fun showBluetoothNotSupportedDialog() {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Bluetooth No Disponible")
                .setMessage("Tu dispositivo no soporta Bluetooth.")
                .setPositiveButton("Entendido") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    fun showBluetoothEnableRationale(enableLauncher: ActivityResultLauncher<Intent>) {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Bluetooth Requerido")
                .setMessage("Para conectar con tu dispositivo ESP32, necesitas habilitar Bluetooth.\n\n¿Deseas habilitarlo ahora?")
                .setPositiveButton("Habilitar") { dialog, _ ->
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableLauncher.launch(enableBtIntent)
                    dialog.dismiss()
                }
                .setNegativeButton("Ahora no") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    fun showBluetoothPermissionRationale(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Permisos Bluetooth")
                .setMessage("Esta app necesita permisos de Bluetooth para conectar con tu dispositivo ESP32.\n\n¿Deseas otorgar los permisos?")
                .setPositiveButton("Permitir") { dialog: DialogInterface, _: Int ->
                    val permissions = PermissionHelper.getRequiredBluetoothPermissions()
                    permissionLauncher.launch(permissions)
                    dialog.dismiss()
                }
                .setNegativeButton("Ahora no") { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    Log.w("PermissionCoordinator", "Usuario rechazó permisos Bluetooth")
                }
                .show()
        }
    }

    fun showNotificationPermissionRationale(permissionLauncher: ActivityResultLauncher<String>?) {
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Permiso de Notificaciones")
                .setMessage("Esta app necesita permisos para mostrar notificaciones de su estado operativo en la barra superior.\n\nSin este permiso, Android podría cerrar la aplicación sorpresivamente.")
                .setPositiveButton("Otorgar Permiso") { dialog, _ ->
                    dialog.dismiss()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .setNegativeButton("Más tarde") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton("Abrir Ajustes") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", activity.packageName, null)
                        }
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("PermissionCoordinator", "No se pudo abrir settings", e)
                    }
                }
                .setCancelable(false)
                .show()
        }
    }
}
