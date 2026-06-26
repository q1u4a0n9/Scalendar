package com.scalendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.navigation.AppNavigation
import com.scalendar.ui.screen.settings.SettingsViewModel
import com.scalendar.ui.theme.ScalendarTheme
import com.scalendar.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Request POST_NOTIFICATIONS at runtime (required on Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless of result — user can grant later in Settings */ }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLang(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // Đọc theme setting; hiltViewModel() trả về cùng instance với AppNavigation
            val settingsVm: SettingsViewModel = hiltViewModel()
            val uiState by settingsVm.uiState.collectAsState()
            val darkTheme = when (uiState.theme) {
                "DARK"  -> true
                "LIGHT" -> false
                else    -> isSystemInDarkTheme()   // "SYSTEM"
            }

            LaunchedEffect(uiState.lang) {
                LocaleHelper.setLang(this@MainActivity, uiState.lang)
                val currentLocale = resources.configuration.locales[0]
                val expectedLang  = if (uiState.lang == "EN") "en" else "vi"
                if (currentLocale.language != expectedLang) recreate()
            }

            ScalendarTheme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }
}
