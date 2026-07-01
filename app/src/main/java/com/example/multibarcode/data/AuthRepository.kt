package com.example.multibarcode.data

import android.content.Context
import com.example.multibarcode.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Google-only authentication, backed by Firebase Auth. */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun googleClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user
    }

    fun signOut(context: Context) {
        auth.signOut()
        googleClient(context).signOut()
    }

    companion object {
        /** Primary super-admin: always allowed to sign in and manage the allowlist. */
        const val SUPER_ADMIN_EMAIL = "osahsh991@gmail.com"

        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun get(): AuthRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository().also { INSTANCE = it }
            }
    }
}
