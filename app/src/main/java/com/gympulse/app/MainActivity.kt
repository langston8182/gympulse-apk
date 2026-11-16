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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    Box(Modifier.fillMaxSize()) {
                        GymPulseApp(
                            appViewModel = appViewModel,
                            openWorkoutRequested = openWorkout,
                            onOpenWorkoutConsumed = { openWorkoutRequest.value = false },
                        )
                        if (BuildConfig.API_ENV != "prod") EnvironmentBadge(BuildConfig.API_ENV, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppOpened()
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

/** Always-visible marker on non-production builds so they can't be mistaken for the real app. */
@Composable
private fun EnvironmentBadge(env: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Full-width stripe at the very top: visible whatever the notch or status bar content.
        Box(Modifier.fillMaxWidth().height(3.dp).background(ComposeColor(0xFFE65100)))
        Box(
            Modifier
                .statusBarsPadding()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(ComposeColor(0xFFE65100))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            Text(env.uppercase(), color = ComposeColor.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
