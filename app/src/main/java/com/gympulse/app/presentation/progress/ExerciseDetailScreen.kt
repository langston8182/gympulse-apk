package com.gympulse.app.presentation.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.charts.LineChart
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.EvoTopBar
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.PeriodComparison
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.stats.ExerciseMetric
import com.gympulse.app.presentation.common.SetFormat

private val repsTargets = listOf(1, 3, 5, 8, 10, 12)

private fun metricValue(metric: ExerciseMetric, value: Double, unit: WeightUnit): String = when (metric) {
    ExerciseMetric.ESTIMATED_ONE_REP_MAX -> "~" + Formatters.weight(value, unit)
    ExerciseMetric.MAX_WEIGHT, ExerciseMetric.WEIGHT_AT_REPS -> Formatters.weight(value, unit)
    ExerciseMetric.VOLUME -> Formatters.volume(value, unit)
    ExerciseMetric.REPETITIONS -> "${value.toInt()} reps"
    ExerciseMetric.BEST_DURATION, ExerciseMetric.TOTAL_DURATION -> Formatters.seconds(value.toInt())
}

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val exercise = state.exercise
    Scaffold(
        topBar = {
            EvoTopBar(
                title = state.name.ifBlank { "Exercice" },
                onBack = onBack,
                actions = {
                    if (exercise != null) {
                        IconButton(onClick = { onEdit(exercise.id) }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifier l'exercice") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            exercise == null && state.performances.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Exercice introuvable",
                message = "Il a peut-être été supprimé.",
                actionLabel = "Retour",
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") { Header(state, unit) }
                if (state.performances.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.FitnessCenter,
                            title = "Aucune séance enregistrée",
                            message = "Les graphiques et records apparaîtront après votre première séance avec cet exercice.",
                        )
                    }
                } else {
                    item(key = "kpis") { KeyFigures(state, unit) }
                    item(key = "chart") { ChartSection(state, viewModel, unit) }
                    state.comparison?.let { comparison -> item(key = "comparison") { ComparisonCard(state, comparison, unit) } }
                    item(key = "records") { RecordsCard(state, unit) }
                    val repMaxes = state.progress?.repMaxes.orEmpty()
                    if (state.type == ExerciseType.REPETITIONS && repMaxes.isNotEmpty()) {
                        item(key = "repmax") { RepMaxTable(state, unit) }
                    }
                    item(key = "history-header") { SectionHeader("Historique", subtitle = "${state.performances.size} séances") }
                    items(state.performances.asReversed().take(40), key = { it.executionId }) { performance ->
                        EvoCard(contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Formatters.fullDate(performance.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(performance.sessionName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        SetFormat.performedSummary(performance.sets, performance.type, unit),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    performance.estimatedOneRepMaxKg?.let {
                                        Text("~${Formatters.weight(it, unit)}", style = MaterialTheme.typography.labelLarge)
                                        Text("1RM est.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    performance.bestDurationSeconds?.let { Text(Formatters.seconds(it), style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: ExerciseDetailState, unit: WeightUnit) {
    val exercise = state.exercise
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExerciseImage(
            imageUri = exercise?.imageUri,
            size = null,
            shape = RoundedCornerShape(28.dp),
            contentDescription = state.name,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Text(state.name, style = MaterialTheme.typography.headlineMedium)
        if (exercise != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(exercise.type.label, icon = if (exercise.isTimed) Icons.Rounded.Timer else Icons.Rounded.FitnessCenter)
                exercise.muscleGroups.forEachIndexed { index, muscle ->
                    Pill(
                        muscle.label,
                        containerColor = if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                exercise.equipment.forEach { Pill(it.label) }
            }
            if (exercise.loadMode == LoadMode.PER_IMPLEMENT) {
                InfoLine("Charge saisie par haltère : le volume compte les deux haltères.")
            }
            if (exercise.notes.isNotBlank()) {
                Text(exercise.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InfoLine("Cet exercice a été supprimé de la bibliothèque ; son historique est conservé.")
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun KeyFigures(state: ExerciseDetailState, unit: WeightUnit) {
    val progress = state.progress ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Séances", "${progress.performances.size}", Modifier.weight(1f), icon = Icons.Rounded.CalendarToday)
            StatTile("Séries", "${progress.totalSets}", Modifier.weight(1f), icon = Icons.Rounded.Layers, accent = MaterialTheme.colorScheme.secondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.type == ExerciseType.DURATION) {
                StatTile("Meilleure durée", progress.maxDurationSeconds?.let { Formatters.seconds(it) } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.Timer, accent = GymPulseTheme.extendedColors.record)
                StatTile("Durée totale", Formatters.seconds(progress.performances.sumOf { it.totalDurationSeconds }), Modifier.weight(1f), icon = Icons.Rounded.Timer, accent = MaterialTheme.colorScheme.tertiary)
            } else {
                StatTile("Charge max", progress.maxWeightKg?.let { Formatters.weight(it, unit) } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.MilitaryTech, accent = GymPulseTheme.extendedColors.record)
                StatTile("1RM estimé", progress.bestOneRepMaxKg?.let { "~" + Formatters.weight(it, unit) } ?: "—", Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter, accent = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun ChartSection(state: ExerciseDetailState, viewModel: ExerciseDetailViewModel, unit: WeightUnit) {
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Évolution", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        PillSelector(StatsPeriod.entries, state.selection.period, viewModel::setPeriod, label = { it.label })
        Spacer(Modifier.height(8.dp))
        if (state.metrics.size > 1) {
            PillSelector(state.metrics, state.metric, viewModel::setMetric, label = { it.label })
            Spacer(Modifier.height(8.dp))
        }
        if (state.metric == ExerciseMetric.WEIGHT_AT_REPS) {
            PillSelector(repsTargets, state.selection.minRepetitions, viewModel::setMinRepetitions, label = { "≥ $it reps" })
            Spacer(Modifier.height(6.dp))
            InfoLine("Compare uniquement les séries d'au moins ${state.selection.minRepetitions} répétitions, pour une progression à effort équivalent.")
        }
        if (state.metric == ExerciseMetric.ESTIMATED_ONE_REP_MAX) {
            InfoLine("1RM estimé (formule d'Epley, séries de 1 à 12 reps) : valeur indicative.")
        }
        Spacer(Modifier.height(12.dp))
        LineChart(
            points = state.series,
            valueFormatter = { metricValue(state.metric, it, unit) },
            axisFormatter = {
                if (state.metric == ExerciseMetric.VOLUME) Formatters.volumeCompact(it, unit) else metricValue(state.metric, it, unit).removePrefix("~")
            },
            dateFormatter = Formatters::dayMonth,
            contentDescription = "Évolution ${state.metric.label} de ${state.name}",
            color = when (state.metric) {
                ExerciseMetric.VOLUME -> MaterialTheme.colorScheme.tertiary
                ExerciseMetric.REPETITIONS, ExerciseMetric.TOTAL_DURATION -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            },
            emptyMessage = "Aucune donnée sur cette période",
        )
        if (state.series.size >= 2) {
            val first = state.series.first().value
            val last = state.series.last().value
            if (first > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${metricValue(state.metric, first, unit)} → ${metricValue(state.metric, last, unit)} (${Formatters.percentChange((last - first) / first)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: ExerciseDetailState, comparison: PeriodComparison, unit: WeightUnit) {
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Comparaison de périodes", style = MaterialTheme.typography.titleMedium)
        Text(
            "Période sélectionnée vs période précédente de même durée",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        CompareLine("Séances", comparison.previousSessions.toDouble(), comparison.currentSessions.toDouble()) { it.toInt().toString() }
        if (state.type == ExerciseType.DURATION) {
            CompareLine("Meilleure durée", comparison.previousBestDuration?.toDouble(), comparison.currentBestDuration?.toDouble()) { Formatters.seconds(it.toInt()) }
        } else {
            CompareLine("1RM estimé", comparison.previousBestOneRepMax, comparison.currentBestOneRepMax) { "~" + Formatters.weight(it, unit) }
            CompareLine("Charge max", comparison.previousMaxWeight, comparison.currentMaxWeight) { Formatters.weight(it, unit) }
            CompareLine("Volume", comparison.previousVolume, comparison.currentVolume) { Formatters.volume(it, unit) }
        }
    }
}

@Composable
private fun CompareLine(label: String, before: Double?, after: Double?, format: (Double) -> String) {
    val success = GymPulseTheme.extendedColors.success
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
        Text(before?.let(format) ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(after?.let(format) ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val delta = if (before != null && after != null && before > 0) (after - before) / before else null
        Text(
            delta?.let { Formatters.percentChange(it) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = when {
                delta == null -> Color.Unspecified
                delta > 0.005 -> success
                delta < -0.005 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun RecordsCard(state: ExerciseDetailState, unit: WeightUnit) {
    val progress = state.progress ?: return
    val gold = GymPulseTheme.extendedColors.record
    val rows = buildList {
        if (state.type == ExerciseType.DURATION) {
            progress.maxDurationSeconds?.let { add("Meilleure durée" to Formatters.seconds(it)) }
        } else {
            progress.maxWeightKg?.let { max ->
                val best = progress.performances.filter { it.topWeightKg == max }.maxByOrNull { it.topWeightRepetitions ?: 0 }
                add("Charge max" to (Formatters.weight(max, unit) + (best?.topWeightRepetitions?.let { " × $it" } ?: "") + (best?.let { " · ${Formatters.dayMonth(it.date)}" } ?: "")))
            }
            progress.bestOneRepMaxKg?.let { add("1RM estimé (indicatif)" to "~" + Formatters.weight(it, unit)) }
            progress.bestSetVolumeKg?.let { add("Meilleure série (volume)" to Formatters.volume(it, unit)) }
            progress.bestSessionVolumeKg?.let { add("Meilleur volume sur une séance" to Formatters.volume(it, unit)) }
            progress.maxRepetitions?.let { add("Répétitions max sur une série" to "$it reps") }
        }
        add("Répétitions totales" to Formatters.integer(progress.totalRepetitions))
    }
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MilitaryTech, null, tint = gold)
            Spacer(Modifier.width(8.dp))
            Text("Records personnels", style = MaterialTheme.typography.titleMedium)
        }
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall, color = if (index == 0) gold else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun RepMaxTable(state: ExerciseDetailState, unit: WeightUnit) {
    val repMaxes = state.progress?.repMaxes.orEmpty()
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Records par répétitions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Charge la plus lourde soulevée pour au moins N répétitions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        repMaxes.forEachIndexed { index, repMax ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${repMax.repetitions} RM", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                Text(Formatters.weight(repMax.weightKg, unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Formatters.dayMonthYear(repMax.achievedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
