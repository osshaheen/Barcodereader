package com.example.multibarcode.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AppRepository
import com.example.multibarcode.data.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthPhase { LOADING, SIGNED_OUT, AUTHORIZED }

data class AuthUiState(
    val phase: AuthPhase = AuthPhase.LOADING,
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthRepository.get()
    private val data = AppRepository.get()

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        // Restore a previous session, but re-check the allowlist each launch.
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT)
            } else {
                verifyAllowed(user.email)
            }
        }
    }

    fun googleClient(): GoogleSignInClient = auth.googleClient(getApplication())

    fun onGoogleIdToken(idToken: String?) {
        if (idToken.isNullOrBlank()) {
            _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = "تعذّر الحصول على بيانات جوجل")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val user = auth.signInWithGoogle(idToken)
                verifyAllowed(user?.email)
            } catch (e: Exception) {
                auth.signOut(getApplication())
                _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = e.message ?: "تعذّر تسجيل الدخول")
            }
        }
    }

    fun onSignInFailed(message: String?) {
        _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = message ?: "تعذّر تسجيل الدخول")
    }

    private suspend fun verifyAllowed(email: String?) {
        val allowed = try {
            email != null && data.isEmailAllowed(email)
        } catch (e: Exception) {
            // Network/permission issue reading the allowlist.
            auth.signOut(getApplication())
            _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = "تعذّر التحقق من الصلاحية: ${e.message}")
            return
        }
        if (allowed) {
            _ui.value = AuthUiState(phase = AuthPhase.AUTHORIZED)
        } else {
            auth.signOut(getApplication())
            _ui.value = AuthUiState(
                phase = AuthPhase.SIGNED_OUT,
                error = "البريد ${email ?: ""} غير مصرّح له بالدخول",
            )
        }
    }

    fun signOut() {
        auth.signOut(getApplication())
        _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT)
    }

    fun clearError() {
        if (_ui.value.error != null) _ui.value = _ui.value.copy(error = null)
    }
}
