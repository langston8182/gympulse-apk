package com.gympulse.app.presentation

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gympulse.app.core.designsystem.components.NumberStepper
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.PerformedSet
import com.gympulse.app.domain.model.RestTimer
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.workout.WorkoutEngine
import com.gympulse.app.presentation.common.SessionCard
import com.gympulse.app.presentation.common.SetFormat
import com.gympulse.app.presentation.workout.RestPanel
import com.gympulse.app.presentation.workout.nextEffortLabel
import com.gympulse.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ComponentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restPanelCountsDownAndOffersControls() {
        val time = mutableLongStateOf(0L)
        var skipped = 0
        val extended = mutableListOf<Int>()
        val rest = RestTimer(startedAt = 0, endsAt = 90_000, totalSeconds = 90, exerciseKey = "k", setIndex = 0)
        compose.setContent {
            GymPulseTheme {
                RestPanel(
                    rest = rest,
                    time = time,
                    justFinished = false,
                    nextLabel = "Squat barre · Série 2/3 · 5 × 100 kg",
                    onSkip = { skipped++ },
                    onExtend = { extended += it },
                )
            }
        }
        compose.onNodeWithText("REPOS").assertIsDisplayed()
        compose.onNodeWithText("01:30").assertIsDisplayed()
        compose.onNodeWithText("Squat barre · Série 2/3 · 5 × 100 kg").assertIsDisplayed()

        compose.onNodeWithText("+30 s").performClick()
        compose.onNodeWithText("+15 s").performClick()
        compose.onNodeWithText("Passer").performClick()
        assertEquals(listOf(30, 15), extended)
        assertEquals(1, skipped)

        time.longValue = 30_000
        compose.onNodeWithText("01:00").assertIsDisplayed()
        time.longValue = 95_000
        compose.onNodeWithText("Série suivante prête !").assertIsDisplayed()
        compose.onNodeWithText("C'est parti").assertIsDisplayed()
    }

    @Test
    fun numberStepperForwardsIncrementsAndShowsValue() {
        val value = mutableStateOf(8)
        compose.setContent {
            GymPulseTheme {
                NumberStepper(
                    label = "Répétitions",
                    value = value.value.toString(),
                    onDecrement = { value.value-- },
                    onIncrement = { value.value++ },
                    onValueClick = {},
                )
            }
        }
        compose.onNodeWithText("8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Augmenter Répétitions").performClick()
        compose.onNodeWithText("9").assertIsDisplayed()
        compose.onNodeWithContentDescription("Diminuer Répétitions").performClick()
        compose.onNodeWithContentDescription("Diminuer Répétitions").performClick()
        compose.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun sessionCardShowsSummaryAndStartsWorkout() {
        var started = 0
        var opened = 0
        val session = TestData.session()
        compose.setContent {
            GymPulseTheme {
                SessionCard(session = session, onClick = { opened++ }, onStart = { started++ })
            }
        }
        compose.onNodeWithText("Push test").assertIsDisplayed()
        compose.onNodeWithText("3 exercices · 9 séries").assertIsDisplayed()
        compose.onNodeWithContentDescription("Démarrer Push test").performClick()
        compose.onNodeWithText("Push test").performClick()
        assertEquals(1, started)
        assertEquals(1, opened)
    }

    @Test
    fun nextEffortLabelDescribesTheUpcomingSet() {
        val execution = WorkoutEngine.completeCurrentSet(
            WorkoutEngine.create(TestData.session(), 0, keyFactory = TestData.keys()),
            12, 20.0, null, 1_000,
        )
        assertEquals("Développé couché haltères · Série 2/4 · 8 × 24 kg", nextEffortLabel(execution, WeightUnit.KG))
        assertEquals(
            "4 × 5 à 32 kg",
            SetFormat.performedSummary(List(4) { PerformedSet(5, 32.0, null) }, ExerciseType.REPETITIONS, WeightUnit.KG),
        )
    }
}
