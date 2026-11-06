package com.gympulse.app.presentation

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.gympulse.app.BuildConfig
import com.gympulse.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end journey on a fresh install against the preprod API: onboarding start
 * the suggested workout, record a set, see the automatic rest, finish and save the workout. It runs
 * with a dedicated server identity whose data is wiped before and after the test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainJourneyTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val permissions: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Before
    fun clearAppData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("gympulse.db")
        File(context.filesDir, "datastore").deleteRecursively()
        context.getSharedPreferences("gympulse_identity", Context.MODE_PRIVATE).edit().putString("installation_id_override", E2E_USER).commit()
        wipeServerData()
        call(
            "POST", "sessions",
            """{"name":"E2E Push","goal":"HYPERTROPHY","exercises":[{"exerciseRef":"bench_db","restSeconds":30,"sets":[{"reps":8,"weightKg":20},{"reps":8,"weightKg":20}]}]}""",
        )
    }

    @After
    fun cleanServer() = wipeServerData()

    private fun wipeServerData() {
        runCatching {
            Regex(""""ref"\s*:\s*"([^"]+)"""").findAll(call("GET", "sessions")).forEach { call("DELETE", "sessions/${it.groupValues[1]}") }
            call("DELETE", "workouts/active")
            Regex(""""ref"\s*:\s*"([^"]+)"""").findAll(call("GET", "workouts?limit=500")).forEach { call("DELETE", "workouts/${it.groupValues[1]}") }
        }
    }

    private fun call(method: String, path: String, body: String? = null): String {
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("x-user-id", E2E_USER)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        return try {
            (if (connection.responseCode < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val E2E_USER = "gympulse-e2e-tests"
    }

    @Test
    fun onboardingThenCompleteWorkout() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasText("Passer"), 30_000)
            compose.onNodeWithText("Passer").performClick()
            compose.onNodeWithText("Commencer").performClick()

            compose.waitUntilAtLeastOneExists(hasText("SÉANCE SUGGÉRÉE AUJOURD'HUI"), 120_000)
            compose.onNodeWithText("Démarrer l'entraînement").performClick()

            compose.waitUntilAtLeastOneExists(hasText("Terminer la série"), 10_000)
            compose.onNodeWithText("Terminer la série").performClick()
            compose.waitUntilAtLeastOneExists(hasText("REPOS"), 5_000)
            compose.onNodeWithText("Passer").performClick()
            compose.waitUntilDoesNotExist(hasText("REPOS"), 5_000)

            compose.onNodeWithContentDescription("Plus d'actions").performClick()
            compose.onNodeWithText("Terminer la séance").performClick()
            compose.onNodeWithText("Terminer").performClick()

            compose.waitUntilAtLeastOneExists(hasText("Terminer et enregistrer"), 10_000)
            compose.onNodeWithText("Terminer et enregistrer").performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Retour"), 10_000)
        }
    }
}
