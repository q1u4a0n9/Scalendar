package com.scalendar.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.scalendar.data.model.EntryCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "scalendar_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ── Keys ──────────────────────────────────────────────────────────

    companion object {
        val KEY_THEME     = stringPreferencesKey("theme")       // "SYSTEM"|"LIGHT"|"DARK"
        val KEY_LANG      = stringPreferencesKey("lang")        // "VI"|"EN"
        val KEY_HOME_VIEW      = stringPreferencesKey("home_view")       // "TODAY"|"WEEK"|"MONTH"
        val KEY_HOLIDAY_VN_MODE= stringPreferencesKey("holiday_vn_mode") // "ALL"|"NATIONAL"|"NONE"

        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_PENDING_NAME    = stringPreferencesKey("pending_display_name")

        fun colorKey(id: String): Preferences.Key<String> =
            stringPreferencesKey("cat_color_$id")

        fun defaultNotifKey(cat: EntryCategory): Preferences.Key<Int> =
            intPreferencesKey("default_notif_${cat.name}")
    }

    // ── General settings ──────────────────────────────────────────────

    val theme: Flow<String>    = context.dataStore.data.map { it[KEY_THEME]     ?: "SYSTEM" }
    val lang: Flow<String>     = context.dataStore.data.map { it[KEY_LANG]      ?: "VI" }
    val homeView      : Flow<String> = context.dataStore.data.map { it[KEY_HOME_VIEW]       ?: "WEEK" }
    val holidayVnMode : Flow<String> = context.dataStore.data.map { it[KEY_HOLIDAY_VN_MODE] ?: "NONE" }

    suspend fun setTheme(v: String)         { context.dataStore.edit { it[KEY_THEME]           = v } }
    suspend fun setLang(v: String)          { context.dataStore.edit { it[KEY_LANG]            = v } }
    suspend fun setHomeView(v: String)      { context.dataStore.edit { it[KEY_HOME_VIEW]       = v } }
    suspend fun setHolidayVnMode(v: String) { context.dataStore.edit { it[KEY_HOLIDAY_VN_MODE] = v } }

    // ── Onboarding ────────────────────────────────────────────────────

    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    val pendingName: Flow<String> =
        context.dataStore.data.map { it[KEY_PENDING_NAME] ?: "" }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    suspend fun setPendingName(name: String) {
        context.dataStore.edit { it[KEY_PENDING_NAME] = name }
    }

    suspend fun clearPendingName() {
        context.dataStore.edit { it.remove(KEY_PENDING_NAME) }
    }

    // ── Category color overrides ──────────────────────────────────────

    /**
     * A single Flow emitting all current color overrides as Map<categoryId, hex>.
     * Key = category id string (EntryCategory.name or "HOLIDAY").
     * Value = 6-char hex without '#', e.g. "1E88E5".
     */
    val allColorOverrides: Flow<Map<String, String>> =
        context.dataStore.data.map { prefs ->
            prefs.asMap()
                .filterKeys  { key -> key.name.startsWith("cat_color_") }
                .mapKeys     { (key, _) -> key.name.removePrefix("cat_color_") }
                .mapValues   { (_, v) -> v as String }
        }

    suspend fun setColorOverride(id: String, hex: String) {
        context.dataStore.edit { it[colorKey(id)] = hex }
    }

    /** One-shot read of all current color overrides (used for Firestore push on sign-up). */
    suspend fun getAllColorOverridesOnce(): Map<String, String> = allColorOverrides.first()

    /**
     * Remove ALL category color overrides from DataStore.
     * Called on sign-out / sign-in so one account's colors don't bleed into another.
     */
    suspend fun clearAllColorOverrides() {
        context.dataStore.edit { prefs ->
            EntryCategory.entries.forEach { cat -> prefs.remove(colorKey(cat.name)) }
            prefs.remove(colorKey("HOLIDAY"))
        }
    }

    // ── Default notifications per EntryCategory ───────────────────────
    // DataStore Preferences cannot store nullable Int; use -1 as sentinel for "no reminder".

    val allDefaultNotifs: Flow<Map<EntryCategory, Int?>> =
        context.dataStore.data.map { prefs ->
            EntryCategory.entries.associateWith { cat ->
                val stored = prefs[defaultNotifKey(cat)]
                if (stored == null) 30 else if (stored < 0) null else stored
            }
        }

    suspend fun setDefaultNotif(cat: EntryCategory, minutes: Int?) {
        context.dataStore.edit { it[defaultNotifKey(cat)] = minutes ?: -1 }
    }
}
