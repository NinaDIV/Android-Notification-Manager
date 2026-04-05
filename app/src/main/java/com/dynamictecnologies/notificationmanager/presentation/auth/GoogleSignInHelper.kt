package com.dynamictecnologies.notificationmanager.presentation.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.dynamictecnologies.notificationmanager.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper para manejar Google Sign In usando la moderna API de Credential Manager.
 * Encapsula la lógica específica de Android, abstrayéndola del ViewModel.
 */
class GoogleSignInHelper(private val context: Context) {
    
    private val credentialManager = CredentialManager.create(context)

    /**
     * Inicia el flujo de Google Sign In suspendiendo la corrutina.
     * Retorna el idToken JWT para Firebase, o null si el usuario canceló.
     * @param activityContext Requiere contexto de Activity para renderizar UI BottomSheet
     */
    suspend fun signInWithGoogle(activityContext: Context): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            handleSignInResult(result)
        } catch (e: GetCredentialCancellationException) {
            // El usuario cerró el diálogo, retornamos null silenciosamente
            null
        } catch (e: GetCredentialException) {
            throw Exception("Fallo en inicio de sesión: ${e.localizedMessage}")
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): String {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                return googleIdTokenCredential.idToken
            } catch (e: GoogleIdTokenParsingException) {
                throw Exception("Error procesando credenciales de Google: ${e.message}")
            }
        }
        throw Exception("Tipo de credencial no soportada")
    }

    /**
     * Limpia el estado persistido de Google Auth cuando se hace Log Out
     */
    suspend fun signOut() {
        try {
            val request = ClearCredentialStateRequest()
            credentialManager.clearCredentialState(request)
        } catch (e: Exception) {
            // Ignorar errores silenciados
        }
    }
}
