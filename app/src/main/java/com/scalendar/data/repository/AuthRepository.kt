package com.scalendar.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Current Firebase user, or null if not signed in. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Quick synchronous check — use `authStateFlow()` for reactive UI. */
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /**
     * Reactive auth state. Emits the current user whenever sign-in / sign-out happens.
     * Always emits immediately with the current state when collected.
     */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Sign in with email + password. Returns [Result.failure] on wrong credentials / network error. */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Đăng nhập thất bại")
    }

    /** Register a new account. Returns [Result.failure] if email already in use or network error. */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Đăng ký thất bại")
    }

    /** Sign in with a Google ID token obtained from GoogleSignIn. */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await().user
            ?: error("Đăng nhập Google thất bại")
    }

    // ── Account info ──────────────────────────────────────────────────

    val displayName: String   get() = auth.currentUser?.displayName ?: ""
    val email: String         get() = auth.currentUser?.email ?: ""
    val isGoogleUser: Boolean get() =
        auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true

    /** Compute 1–3 uppercase initials from display name, or 2 chars from email. */
    fun initials(): String {
        val name = auth.currentUser?.displayName
        if (!name.isNullOrBlank())
            return name.split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(3)
                .joinToString("")
        return auth.currentUser?.email?.take(2)?.uppercase() ?: "?"
    }

    /** Update the Firebase display name. */
    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val req = UserProfileChangeRequest.Builder()
            .setDisplayName(name.trim())
            .build()
        auth.currentUser?.updateProfile(req)?.await()
    }

    /** Permanently delete the current Firebase account. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        auth.currentUser?.delete()?.await()
    }

    fun signOut() { auth.signOut() }
}
