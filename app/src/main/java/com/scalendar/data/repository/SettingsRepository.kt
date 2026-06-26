package com.scalendar.data.repository

import com.scalendar.data.datastore.SettingsDataStore
import com.scalendar.data.model.EntryCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore,
) {
    // ── General ───────────────────────────────────────────────────────
    val theme: Flow<String>    = store.theme
    val lang: Flow<String>     = store.lang
    val homeView      : Flow<String> = store.homeView
    val holidayVnMode : Flow<String> = store.holidayVnMode

    suspend fun setTheme(v: String)         = store.setTheme(v)
    suspend fun setLang(v: String)          = store.setLang(v)
    suspend fun setHomeView(v: String)      = store.setHomeView(v)
    suspend fun setHolidayVnMode(v: String) = store.setHolidayVnMode(v)

    // ── Color overrides ───────────────────────────────────────────────
    val allColorOverrides: Flow<Map<String, String>> = store.allColorOverrides

    suspend fun setColorOverride(id: String, hex: String) =
        store.setColorOverride(id, hex)

    suspend fun getAllColorOverridesOnce(): Map<String, String> =
        store.getAllColorOverridesOnce()

    suspend fun clearAllColorOverrides() =
        store.clearAllColorOverrides()

    // ── Default notifications ─────────────────────────────────────────
    val allDefaultNotifs: Flow<Map<EntryCategory, Int?>> = store.allDefaultNotifs

    suspend fun setDefaultNotif(cat: EntryCategory, minutes: Int?) =
        store.setDefaultNotif(cat, minutes)

    // ── Onboarding ────────────────────────────────────────────────────
    val onboardingCompleted: Flow<Boolean> = store.onboardingCompleted
    val pendingName: Flow<String>          = store.pendingName

    suspend fun setOnboardingCompleted()   = store.setOnboardingCompleted()
    suspend fun setPendingName(n: String)  = store.setPendingName(n)
    suspend fun clearPendingName()         = store.clearPendingName()
}
