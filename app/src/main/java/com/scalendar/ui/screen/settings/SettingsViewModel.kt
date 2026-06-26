package com.scalendar.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scalendar.data.database.entity.UserCalendarEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.repository.AuthRepository
import com.scalendar.data.repository.FirestoreRepository
import com.scalendar.data.repository.SettingsRepository
import com.scalendar.data.repository.UserCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme          : String                      = "SYSTEM",
    val lang           : String                      = "VI",
    val homeView       : String                      = "WEEK",
    val holidayVnMode  : String                      = "ALL",   // "ALL"|"NATIONAL"|"NONE"
    val colorOverrides : Map<String, String>         = emptyMap(),
    val defaultNotifs  : Map<EntryCategory, Int?>    = emptyMap(),
    val userCalendars  : List<UserCalendarEntity>    = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo : SettingsRepository,
    private val userCalRepo  : UserCalendarRepository,
    private val authRepo     : AuthRepository,
    private val firestoreRepo: FirestoreRepository,
) : ViewModel() {

    private val uid get() = authRepo.currentUser?.uid ?: ""

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepo.theme,
            settingsRepo.lang,
            settingsRepo.homeView,
            settingsRepo.holidayVnMode,
        ) { t, l, h, hv -> listOf(t, l, h, hv) },
        settingsRepo.allColorOverrides,
        settingsRepo.allDefaultNotifs,
        userCalRepo.getAll(),
    ) { general, colors, notifs, cals ->
        SettingsUiState(
            theme          = general[0],
            lang           = general[1],
            homeView       = general[2],
            holidayVnMode  = general[3],
            colorOverrides = colors,
            defaultNotifs  = notifs,
            userCalendars  = cals,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ── Onboarding ────────────────────────────────────────────────────
    val onboardingCompleted: StateFlow<Boolean> = settingsRepo.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setOnboardingCompleted() { viewModelScope.launch { settingsRepo.setOnboardingCompleted() } }
    fun setPendingName(name: String) { viewModelScope.launch { settingsRepo.setPendingName(name) } }

    // ── General settings ──────────────────────────────────────────────
    fun setTheme(v: String)         { viewModelScope.launch { settingsRepo.setTheme(v) } }
    fun setLang(v: String)          { viewModelScope.launch { settingsRepo.setLang(v) } }
    fun setHomeView(v: String)      { viewModelScope.launch { settingsRepo.setHomeView(v) } }
    fun setHolidayVnMode(v: String) { viewModelScope.launch { settingsRepo.setHolidayVnMode(v) } }

    /** Raw DataStore flow — used by AppNavigation for one-shot initial-screen navigation. */
    val homeViewFlow: Flow<String> = settingsRepo.homeView

    // ── Category color overrides ──────────────────────────────────────
    /** [id] = EntryCategory.name or "HOLIDAY"; [hex] = 6-char hex without '#'. */
    fun setColorOverride(id: String, hex: String) {
        viewModelScope.launch {
            settingsRepo.setColorOverride(id, hex)
            firestoreRepo.upsertColorOverride(uid, id, hex)   // keep cloud in sync
        }
    }

    // ── Default notifications ─────────────────────────────────────────
    fun setDefaultNotif(cat: EntryCategory, minutes: Int?) {
        viewModelScope.launch { settingsRepo.setDefaultNotif(cat, minutes) }
    }

    // ── User calendars ────────────────────────────────────────────────
    fun addUserCalendar(id: String, name: String, colorHex: String) {
        val cal = UserCalendarEntity(id = id, name = name, colorHex = colorHex)
        viewModelScope.launch {
            userCalRepo.insert(cal)
            firestoreRepo.upsertUserCalendar(uid, cal)
        }
    }

    fun deleteUserCalendar(id: String) {
        viewModelScope.launch {
            userCalRepo.delete(UserCalendarEntity(id = id, name = "", colorHex = ""))
            firestoreRepo.deleteUserCalendar(uid, id)
        }
    }
}
