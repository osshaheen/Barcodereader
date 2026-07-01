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
    val isAdmin: Boolean = false,
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
        val e = email?.trim()?.lowercase()
        if (e == null) {
            auth.signOut(getApplication())
            _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = "تعذّر قراءة بريد الحساب")
            return
        }
        val isAdmin = e == AuthRepository.SUPER_ADMIN_EMAIL
        val allowed = try {
            isAdmin || data.isEmailAllowed(e)
        } catch (ex: Exception) {
            auth.signOut(getApplication())
            _ui.value = AuthUiState(phase = AuthPhase.SIGNED_OUT, error = "تعذّر التحقق من الصلاحية: ${ex.message}")
            return
        }
        if (allowed) {
            _ui.value = AuthUiState(phase = AuthPhase.AUTHORIZED, isAdmin = isAdmin)
        } else {
            // Record a pending request for the admin, then sign out.
            try { data.createAccessRequest(e) } catch (_: Exception) {}
            auth.signOut(getApplication())
            _ui.value = AuthUiState(
                phase = AuthPhase.SIGNED_OUT,
                error = "البريد $e غير مصرّح له بعد. تم إرسال طلبك للمشرف للموافقة.",
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
