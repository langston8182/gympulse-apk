package com.gympulse.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.LocalSnackbarHostState
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.presentation.app.AppViewModel
import com.gympulse.app.presentation.app.GymPulseApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private val openWorkoutRequest = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.uiState.value.loading }
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            val openWorkout by openWorkoutRequest.collectAsStateWithLifecycle()
            val dark = when (state.preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val snackbarHostState = remember { SnackbarHostState() }
            GymPulseTheme(themeMode = state.preferences.themeMode, dynamicColor = state.preferences.dynamicColor) {
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    GymPulseApp(
                        appViewModel = appViewModel,
                        openWorkoutRequested = openWorkout,
                        onOpenWorkoutConsumed = { openWorkoutRequest.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) {
            openWorkoutRequest.value = true
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
        }
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
