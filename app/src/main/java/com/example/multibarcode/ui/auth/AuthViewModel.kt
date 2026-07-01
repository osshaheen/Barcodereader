package com.example.multibarcode.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.multibarcode.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository.get()

    val user: StateFlow<FirebaseUser?> =
        repo.userFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repo.currentUser,
        )

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun submit(email: String, password: String, isSignUp: Boolean) {
        if (email.isBlank() || password.length < 6) {
            _ui.value = AuthUiState(error = "أدخل بريداً صحيحاً وكلمة مرور من ٦ أحرف على الأقل")
            return
        }
        viewModelScope.launch {
            _ui.value = AuthUiState(loading = true)
            try {
                if (isSignUp) repo.signUp(email, password) else repo.signIn(email, password)
                _ui.value = AuthUiState()
            } catch (e: Exception) {
                _ui.value = AuthUiState(error = friendlyError(e))
            }
        }
    }

    fun clearError() {
        if (_ui.value.error != null) _ui.value = _ui.value.copy(error = null)
    }

    fun signOut() = repo.signOut()

    private fun friendlyError(e: Exception): String {
        val m = e.message ?: return "تعذّر تسجيل الدخول"
        return when {
            m.contains("password is invalid", true) || m.contains("credential is incorrect", true) ->
                "كلمة المرور غير صحيحة"
            m.contains("no user record", true) -> "لا يوجد حساب بهذا البريد"
            m.contains("email address is already in use", true) -> "هذا البريد مستخدم بالفعل"
            m.contains("network", true) -> "لا يوجد اتصال بالإنترنت"
            else -> m
        }
    }
}
