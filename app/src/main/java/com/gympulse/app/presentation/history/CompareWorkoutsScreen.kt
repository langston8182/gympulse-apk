package com.gympulse.app.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.ExercisePerformance
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.presentation.common.SetFormat

private data class ExerciseComparison(
    val name: String,
    val type: ExerciseType,
    val before: ExercisePerformance?,
    val after: ExercisePerformance?,
) {
    /** Heavier top set but clearly fewer repetitions: not an equivalent progression. */
    val heavierButFewerReps: Boolean
        get() {
            val b = before ?: return false
            val a = after ?: return false
            val bw = b.topWeightKg ?: return false
            val aw = a.topWeightKg ?: return false
            val br = b.topWeightRepetitions ?: return false
            val ar = a.topWeightRepetitions ?: return false
            return aw > bw && ar < br
        }
}

private fun compareExercises(first: WorkoutExecution, second: WorkoutExecution): List<ExerciseComparison> {
    fun performances(execution: WorkoutExecution) = execution.exercises.mapNotNull { TrainingMath.performance(execution, it) }
        .associateBy { TrainingMath.exerciseKey(it.exerciseId, it.exerciseName) }
    val before = performances(first)
    val after = performances(second)
    val keys = (after.keys + before.keys).distinct()
    return keys.map { key ->
        val reference = after[key] ?: before.getValue(key)
        ExerciseComparison(reference.exerciseName, reference.type, before[key], after[key])
    }
}

@Composable
fun CompareWorkoutsScreen(onBack: () -> Unit, viewModel: CompareWorkoutsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val first = state.first
    val second = state.second
    Scaffold(topBar = { EvoTopBar("Comparaison", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            first == null || second == null -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.CompareArrows,
                title = "Comparaison impossible",
                message = "Une des deux séances n'existe plus.",
                actionLabel = "Retour",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val comparisons = remember(first, second) { compareExercises(first, second) }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "header") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkoutHeader("Avant", first, Modifier.weight(1f))
                            WorkoutHeader("Après", second, Modifier.weight(1f))
                        }
                    }
                    item(key = "totals") { TotalsCard(first, second, unit) }
                    item(key = "exercises-header") { SectionHeader("Par exercice") }
                    items(comparisons, key = { it.name }) { comparison -> ExerciseComparisonCard(comparison, unit) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeader(label: String, execution: WorkoutExecution, modifier: Modifier) {
    EvoCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(execution.sessionName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(Formatters.dayMonthYear(execution.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TotalsCard(first: WorkoutExecution, second: WorkoutExecution, unit: WeightUnit) {
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Totaux", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        DeltaRow("Durée", first.durationMs.toDouble(), second.durationMs.toDouble(), higherIsBetter = null) { Formatters.duration(it.toLong()) }
        DeltaRow("Volume", TrainingMath.workoutVolumeKg(first), TrainingMath.workoutVolumeKg(second)) { Formatters.volume(it, unit) }
        DeltaRow("Séries", TrainingMath.completedSets(first).toDouble(), TrainingMath.completedSets(second).toDouble()) { it.toInt().toString() }
        DeltaRow("Répétitions", TrainingMath.completedRepetitions(first).toDouble(), TrainingMath.completedRepetitions(second).toDouble()) { it.toInt().toString() }
    }
}

@Composable
private fun DeltaRow(
    label: String,
    before: Double?,
    after: Double?,
    higherIsBetter: Boolean? = true,
    format: (Double) -> String,
) {
    val success = GymPulseTheme.extendedColors.success
    val delta = if (before != null && after != null && before > 0) (after - before) / before else null
    val color = when {
        delta == null || higherIsBetter == null -> MaterialTheme.colorScheme.onSurfaceVariant
        (delta > 0.005) == higherIsBetter && kotlin.math.abs(delta) > 0.005 -> success
        kotlin.math.abs(delta) > 0.005 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(before?.let(format) ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(after?.let(format) ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(
            delta?.let { Formatters.percentChange(it) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = if (delta == null) Color.Unspecified else color,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ExerciseComparisonCard(comparison: ExerciseComparison, unit: WeightUnit) {
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text(comparison.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Avant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(comparison.before?.let { SetFormat.performedSummary(it.sets, it.type, unit) } ?: "Non réalisé", style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("Après", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    comparison.after?.let { SetFormat.performedSummary(it.sets, it.type, unit) } ?: "Non réalisé",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (comparison.before != null && comparison.after != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (comparison.type == ExerciseType.DURATION) {
                DeltaRow("Meilleure durée", comparison.before.bestDurationSeconds?.toDouble(), comparison.after.bestDurationSeconds?.toDouble()) { Formatters.seconds(it.toInt()) }
            } else {
                DeltaRow("Charge max", comparison.before.topWeightKg, comparison.after.topWeightKg) { Formatters.weight(it, unit) }
                DeltaRow("1RM estimé", comparison.before.estimatedOneRepMaxKg, comparison.after.estimatedOneRepMaxKg) { "~" + Formatters.weight(it, unit) }
                DeltaRow("Volume", comparison.before.volumeKg, comparison.after.volumeKg) { Formatters.volume(it, unit) }
            }
            if (comparison.heavierButFewerReps) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = GymPulseTheme.extendedColors.warning)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Charge ↑ mais répétitions ↓ : progression à nuancer, voir le 1RM estimé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GymPulseTheme.extendedColors.warning,
                    )
                }
            }
        }
    }
}
