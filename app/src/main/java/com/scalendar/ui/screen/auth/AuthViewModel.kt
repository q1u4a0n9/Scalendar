package com.scalendar.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.database.entity.UserCalendarEntity
import com.scalendar.data.repository.AuthRepository
import com.scalendar.data.repository.EntryRepository
import com.scalendar.data.repository.FirestoreRepository
import com.scalendar.data.repository.NoteRepository
import com.scalendar.data.repository.SettingsRepository
import com.scalendar.data.repository.UserCalendarRepository
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMsg : String? = null,
)

data class AccountState(
    val displayName : String  = "",
    val email       : String  = "",
    val initials    : String  = "",
    val isGoogleUser: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo     : AuthRepository,
    private val firestoreRepo: FirestoreRepository,
    private val entryRepo    : EntryRepository,
    private val noteRepo     : NoteRepository,
    private val userCalRepo  : UserCalendarRepository,
    private val settingsRepo : SettingsRepository,
) : ViewModel() {

    /** True while a logged-in user session is active. Drives the auth gate in AppNavigation. */
    val isLoggedIn: StateFlow<Boolean> = authRepo.authStateFlow()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepo.isLoggedIn)

    val currentUid: String? get() = authRepo.currentUser?.uid

    /**
     * Incremented after each successful [updateDisplayName] call so that [accountState]
     * re-reads the Firebase user object even though [FirebaseAuth.AuthStateListener] does
     * not fire on profile-only updates (it only fires on sign-in / sign-out).
     */
    private val _profileUpdateTick = MutableStateFlow(0)

    /** Real Firebase user info — drives AccountScreen. */
    val accountState: StateFlow<AccountState> = combine(
        authRepo.authStateFlow(),
        _profileUpdateTick,
    ) { _, _ ->
        // Always re-read authRepo.currentUser so profile updates are reflected immediately.
        val user = authRepo.currentUser
        if (user == null) AccountState()
        else AccountState(
            displayName  = user.displayName ?: "",
            email        = user.email ?: "",
            initials     = authRepo.initials(),
            isGoogleUser = user.providerData.any { it.providerId == "google.com" },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ── Auth actions ──────────────────────────────────────────────────

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMsg = null) }
            authRepo.signIn(email, password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    applyPendingName()
                    pullFromFirestore(user)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMsg = friendlyError(e.message)) }
                }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMsg = null) }
            authRepo.signUp(email, password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    applyPendingName()
                    // New account — push existing local data up
                    pushLocalToFirestore(user)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMsg = friendlyError(e.message)) }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMsg = null) }
            authRepo.signInWithGoogle(idToken)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    applyPendingName()
                    // New user → push local; existing user → pull from cloud
                    val isNewUser = user.metadata?.creationTimestamp == user.metadata?.lastSignInTimestamp
                    if (isNewUser) pushLocalToFirestore(user) else pullFromFirestore(user)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMsg = friendlyError(e.message)) }
                }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            authRepo.updateDisplayName(name)
            // Kick accountState to re-read the updated Firebase user object.
            _profileUpdateTick.update { it + 1 }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            entryRepo.deleteAll()
            noteRepo.deleteAll()
            userCalRepo.deleteAll()
            settingsRepo.clearAllColorOverrides()
            authRepo.deleteAccount()
            // isLoggedIn → false → AppNavigation chuyển về AuthScreen tự động
        }
    }

    fun signOut() {
        viewModelScope.launch {
            entryRepo.deleteAll()
            noteRepo.deleteAll()
            userCalRepo.deleteAll()
            settingsRepo.clearAllColorOverrides()
            authRepo.signOut()
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMsg = null) } }

    // ── Pending name (from onboarding) ───────────────────────────────

    /**
     * If user entered a name during onboarding, apply it as Firebase displayName
     * right after a successful sign-in/sign-up, then clear the stored name.
     */
    private fun applyPendingName() {
        viewModelScope.launch {
            val name = settingsRepo.pendingName.first()
            if (name.isNotBlank()) {
                authRepo.updateDisplayName(name)
                settingsRepo.clearPendingName()
            }
        }
    }

    // ── Sync helpers ──────────────────────────────────────────────────

    /**
     * Clear local Room data then pull fresh data from Firestore.
     * Called after [signIn] — ensures no data leakage from a previous account session.
     */
    private fun pullFromFirestore(user: FirebaseUser) {
        val uid = user.uid
        viewModelScope.launch {
            // Clear existing local data first so the previous user's data doesn't bleed through
            entryRepo.deleteAll()
            noteRepo.deleteAll()
            userCalRepo.deleteAll()
            settingsRepo.clearAllColorOverrides()

            val entries = firestoreRepo.fetchEntries(uid)
            val notes   = firestoreRepo.fetchNotes(uid)
            val cals    = firestoreRepo.fetchUserCalendars(uid)
            val colors  = firestoreRepo.fetchColorOverrides(uid)

            entries.forEach { entryRepo.insert(it) }
            notes.forEach   { noteRepo.insert(it) }
            cals.forEach    { userCalRepo.insert(it) }
            colors.forEach  { (id, hex) -> settingsRepo.setColorOverride(id, hex) }
        }
    }

    /**
     * Push all existing local data up to Firestore.
     * Called after [signUp] so existing entries are backed up immediately.
     */
    private fun pushLocalToFirestore(user: FirebaseUser) {
        val uid = user.uid
        viewModelScope.launch {
            // One-shot reads from Room (not observable Flows)
            runCatching { entryRepo.getAllOnce().forEach  { firestoreRepo.upsertEntry(uid, it) } }
            runCatching { noteRepo.getAllOnce().forEach   { firestoreRepo.upsertNote(uid, it) } }
            runCatching { userCalRepo.getAllOnce().forEach{ firestoreRepo.upsertUserCalendar(uid, it) } }
            // Also push any existing color overrides from DataStore
            runCatching {
                val colors = settingsRepo.getAllColorOverridesOnce()
                firestoreRepo.upsertAllColorOverrides(uid, colors)
            }
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────

    private fun friendlyError(msg: String?): String = when {
        msg == null                              -> "Đã xảy ra lỗi"
        "INVALID_EMAIL" in msg                   -> "Email không hợp lệ"
        "WRONG_PASSWORD" in msg
            || "INVALID_LOGIN_CREDENTIALS" in msg -> "Email hoặc mật khẩu không đúng"
        "EMAIL_ALREADY_IN_USE" in msg            -> "Email này đã được đăng ký"
        "WEAK_PASSWORD" in msg                   -> "Mật khẩu quá yếu (tối thiểu 6 ký tự)"
        "USER_NOT_FOUND" in msg                  -> "Không tìm thấy tài khoản"
        "NETWORK_ERROR" in msg
            || "network" in msg.lowercase()      -> "Lỗi kết nối mạng"
        else                                     -> msg
    }
}
