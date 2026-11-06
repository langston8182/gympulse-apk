package com.gympulse.app.presentation.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gympulse.app.core.designsystem.charts.BarChart
import com.gympulse.app.core.designsystem.charts.Sparkline
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.SearchField
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.components.TrendLabel
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.core.util.longLabel
import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.Granularity
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutTotals
import com.gympulse.app.presentation.common.SetFormat
import com.gympulse.app.presentation.workout.formatRecordValue
import java.time.Instant
import java.time.ZoneId

internal fun bucketDateFormatter(granularity: Granularity, period: StatsPeriod): (Long) -> String = when {
    granularity == Granularity.DAY && period == StatsPeriod.WEEK -> Formatters::shortDay
    granularity == Granularity.MONTH -> Formatters::shortMonth
    else -> Formatters::dayMonth
}

private fun ratio(current: Double, previous: Double): Double? = if (previous > 0) (current - previous) / previous else null

@Composable
fun ProgressScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onCompare: (Long, Long) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Progression",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            )
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Vue d'ensemble") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Historique") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Exercices") })
            }
            when {
                state.loading -> LoadingState()
                state.history.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = "Pas encore de données",
                    message = "Terminez votre première séance pour voir votre progression, vos records et votre historique.",
                )
                tab == 0 -> OverviewTab(state, viewModel::setPeriod, onOpenExercise)
                tab == 1 -> HistoryTab(state, onOpenWorkout, onCompare)
                else -> ExercisesTab(state, viewModel::setQuery, onOpenExercise)
            }
        }
    }
}

@Composable
private fun OverviewTab(state: ProgressState, onPeriod: (StatsPeriod) -> Unit, onOpenExercise: (Long) -> Unit) {
    val dashboard = state.dashboard ?: return
    val unit = LocalWeightUnit.current
    val formatter = bucketDateFormatter(dashboard.granularity, dashboard.period)
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PillSelector(StatsPeriod.entries, state.period, onPeriod, label = { it.label })
                Text(dashboard.period.longLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "kpis") { KpiGrid(dashboard.periodTotals, dashboard.previousPeriodTotals, dashboard.frequencyPerWeek, dashboard.period, unit) }
        item(key = "volume") {
            ChartCard("Volume", "Charge × répétitions") {
                BarChart(
                    points = dashboard.volumeSeries,
                    valueFormatter = { Formatters.volume(it, unit) },
                    axisFormatter = { Formatters.volumeCompact(it, unit) },
                    dateFormatter = formatter,
                    contentDescription = "Volume total ${Formatters.volume(dashboard.periodTotals.volumeKg, unit)}",
                )
            }
        }
        item(key = "frequency") {
            ChartCard("Fréquence", "${Formatters.number(dashboard.frequencyPerWeek)} séance(s) par semaine en moyenne") {
                BarChart(
                    points = dashboard.frequencySeries,
                    valueFormatter = { "${it.toInt()} séance(s)" },
                    axisFormatter = { "${it.toInt()}" },
                    dateFormatter = bucketDateFormatter(dashboard.frequencyGranularity, dashboard.period),
                    contentDescription = "${dashboard.periodTotals.workouts} séances sur la période",
                    color = MaterialTheme.colorScheme.tertiary,
                    height = 170.dp,
                )
            }
        }
        item(key = "duration") {
            ChartCard("Temps d'entraînement", Formatters.hours(dashboard.periodTotals.durationMs) + " au total") {
                BarChart(
                    points = dashboard.durationSeries,
                    valueFormatter = { Formatters.duration((it * 60_000).toLong()) },
                    dateFormatter = formatter,
                    contentDescription = "Durée totale ${Formatters.duration(dashboard.periodTotals.durationMs)}",
                    color = MaterialTheme.colorScheme.secondary,
                    height = 170.dp,
                )
            }
        }
        if (dashboard.muscleDistribution.isNotEmpty()) {
            item(key = "muscles") { MuscleDistribution(dashboard) }
        }
        if (dashboard.recentRecords.isNotEmpty()) {
            item(key = "records-header") { SectionHeader("Records récents", subtitle = "Meilleures performances battues") }
            items(dashboard.recentRecords, key = { "rec-${it.executionId}-${it.exerciseName}-${it.type}" }) { record ->
                val gold = GymPulseTheme.extendedColors.record
                EvoCard(onClick = record.exerciseId?.let { id -> { onOpenExercise(id) } }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MilitaryTech, null, tint = gold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${record.type.label} · ${Formatters.dayMonth(record.achievedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatRecordValue(record, unit), style = MaterialTheme.typography.titleSmall, color = gold, fontWeight = FontWeight.Bold)
                            record.previousValue?.let {
                                Text(
                                    "avant ${formatRecordValue(record.copy(value = it, repetitions = null), unit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (dashboard.topExercises.isNotEmpty()) {
            item(key = "top-header") {
                SectionHeader("Exercices principaux", subtitle = "Tendance sur la période (1RM estimé : indicatif)")
            }
            items(dashboard.topExercises, key = { "top-${it.exerciseId}-${it.exerciseName}" }) { trend ->
                EvoCard(onClick = trend.exerciseId?.let { id -> { onOpenExercise(id) } }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(trend.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${trend.sessions} séances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            trend.changeRatio?.let { TrendLabel(it) }
                        }
                        Sparkline(trend.series, Modifier.width(110.dp).height(44.dp), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiGrid(current: WorkoutTotals, previous: WorkoutTotals, frequency: Double, period: StatsPeriod, unit: WeightUnit) {
    val compare = period != StatsPeriod.ALL
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Séances", "${current.workouts}", Modifier.weight(1f), icon = Icons.Rounded.CalendarToday,
                trend = if (compare) ratio(current.workouts.toDouble(), previous.workouts.toDouble()) else null,
            )
            StatTile(
                "Durée totale", Formatters.duration(current.durationMs), Modifier.weight(1f), icon = Icons.Rounded.Schedule,
                accent = MaterialTheme.colorScheme.secondary,
                trend = if (compare) ratio(current.durationMs.toDouble(), previous.durationMs.toDouble()) else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Volume", Formatters.volume(current.volumeKg, unit), Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter,
                accent = MaterialTheme.colorScheme.tertiary,
                trend = if (compare) ratio(current.volumeKg, previous.volumeKg) else null,
            )
            StatTile(
                "Séries", Formatters.integer(current.sets), Modifier.weight(1f), icon = Icons.Rounded.Layers,
                accent = GymPulseTheme.extendedColors.chart[3],
                trend = if (compare) ratio(current.sets.toDouble(), previous.sets.toDouble()) else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Répétitions", Formatters.integer(current.repetitions), Modifier.weight(1f), icon = Icons.Rounded.Repeat, accent = GymPulseTheme.extendedColors.chart[4])
            StatTile("Fréquence", "${Formatters.number(frequency)} /sem.", Modifier.weight(1f), icon = Icons.Rounded.Speed, accent = GymPulseTheme.extendedColors.chart[5])
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MuscleDistribution(dashboard: DashboardStats) {
    val max = dashboard.muscleDistribution.maxOf { it.second }.coerceAtLeast(1)
    val palette = GymPulseTheme.extendedColors.chart
    EvoCard(contentPadding = PaddingValues(16.dp)) {
        Text("Répartition musculaire", style = MaterialTheme.typography.titleMedium)
        Text("Séries par muscle principal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        dashboard.muscleDistribution.take(8).forEachIndexed { index, (muscle, sets) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(muscle.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$sets séries", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { sets.toFloat() / max },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = palette[index % palette.size],
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun HistoryTab(state: ProgressState, onOpenWorkout: (Long) -> Unit, onCompare: (Long, Long) -> Unit) {
    val unit = LocalWeightUnit.current
    var compareMode by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    val grouped = remember(state.history) {
        state.history.groupBy {
            val date = Instant.ofEpochMilli(it.execution.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            date.year * 100 + date.monthValue
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "compare-toggle") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.history.size} séances enregistrées",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = compareMode,
                        onClick = {
                            compareMode = !compareMode
                            selected.clear()
                        },
                        label = { Text("Comparer") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, null, Modifier.size(18.dp)) },
                    )
                }
            }
            grouped.forEach { (_, items) ->
                historyMonth(items, compareMode, selected, unit, onOpenWorkout)
            }
        }
        AnimatedVisibility(
            visible = compareMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Text(
                        if (selected.size < 2) "Sélectionnez 2 séances (${selected.size}/2)" else "2 séances sélectionnées",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val ordered = state.history.map { it.execution }.filter { it.id in selected }.sortedBy { it.startedAt }
                            onCompare(ordered[0].id, ordered[1].id)
                        },
                        enabled = selected.size == 2,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Comparer ces 2 séances") }
                }
            }
        }
    }
}

private fun LazyListScope.historyMonth(
    items: List<HistoryItem>,
    compareMode: Boolean,
    selected: MutableList<Long>,
    unit: WeightUnit,
    onOpenWorkout: (Long) -> Unit,
) {
    val first = items.first().execution
    item(key = "month-${first.id}") {
        Text(
            Formatters.monthYear(first.startedAt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(items, key = { it.execution.id }) { item ->
        val execution = item.execution
        val isSelected = execution.id in selected
        EvoCard(
            onClick = {
                if (compareMode) {
                    if (isSelected) selected.remove(execution.id) else if (selected.size < 2) selected.add(execution.id)
                } else {
                    onOpenWorkout(execution.id)
                }
            },
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Formatters.fullDate(execution.startedAt)} · ${Formatters.time(execution.startedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(execution.sessionName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (execution.isDemo) DemoBadge()
                if (compareMode) {
                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
                } else {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GoalBadge(execution.trainingGoal)
                Pill(Formatters.duration(execution.durationMs), icon = Icons.Rounded.Schedule)
                Pill(Formatters.volume(item.volumeKg, unit), icon = Icons.Rounded.FitnessCenter)
                Pill("${item.sets} séries")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.exerciseNames.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            execution.locationName?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExercisesTab(state: ProgressState, onQuery: (String) -> Unit, onOpenExercise: (Long) -> Unit) {
    val unit = LocalWeightUnit.current
    Column(Modifier.fillMaxSize()) {
        SearchField(state.query, onQuery, "Rechercher un exercice", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        if (state.exercises.isEmpty()) {
            EmptyState(icon = Icons.Rounded.SearchOff, title = "Aucun exercice", message = "Aucun exercice réalisé ne correspond à cette recherche.")
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.exercises, key = { "${it.exerciseId}-${it.name}" }) { item ->
                val id = item.exerciseId
                EvoCard(onClick = id?.let { { onOpenExercise(it) } }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseImage(item.imageUri, size = 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${item.sessions} séances · dernière ${Formatters.relativeDay(item.last.date).lowercase()}" + if (id == null) " · exercice supprimé" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                SetFormat.performedSummary(item.last.sets, item.type, unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val best = when {
                                item.type == ExerciseType.DURATION -> item.bestDurationSeconds?.let { "Meilleure durée ${Formatters.seconds(it)}" }
                                else -> item.bestOneRepMaxKg?.let { "1RM estimé ~${Formatters.weight(it, unit)}" }
                            }
                            if (best != null) Text(best, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Sparkline(item.series, Modifier.width(80.dp).height(36.dp), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
