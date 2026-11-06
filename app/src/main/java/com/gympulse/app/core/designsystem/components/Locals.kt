package com.gympulse.app.core.designsystem.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.gympulse.app.domain.model.WeightUnit
import kotlinx.coroutines.delay

/** Unit chosen by the user; all weights are stored in kg and converted for display only. */
val LocalWeightUnit = compositionLocalOf { WeightUnit.KG }

/** App-wide snackbar, for short confirmations and errors. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Current time ticking every [intervalMs], read from [now] (the injected monotonic clock).
 * Only the composables reading the returned state recompose on each tick.
 */
@Composable
fun rememberTicker(now: () -> Long, intervalMs: Long = 250L, enabled: Boolean = true): State<Long> =
    produceState(initialValue = now(), enabled, intervalMs) {
        while (enabled) {
            value = now()
            delay(intervalMs)
        }
    }
