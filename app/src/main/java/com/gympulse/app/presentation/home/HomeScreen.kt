package com.gympulse.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import com.gympulse.app.domain.location.SessionSuggestion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gympulse.app.core.designsystem.components.BigActionButton
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EmptyState
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.GradientCard
import com.gympulse.app.core.designsystem.components.LoadingState
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.components.PillSelector
import com.gympulse.app.core.designsystem.components.ProgressRing
import com.gympulse.app.core.designsystem.components.SectionHeader
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.components.TrendLabel
import com.gympulse.app.core.designsystem.components.rememberTicker
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.core.util.longLabel
import com.gympulse.app.domain.model.DashboardStats
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.Granularity
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.WeightUnit
import com.gympulse.app.domain.model.WorkoutExecution
import com.gympulse.app.domain.stats.TrainingMath
import com.gympulse.app.presentation.common.CompatibilityPill
import com.gympulse.app.presentation.common.LocationSelectorChip
import com.gympulse.app.presentation.workout.formatRecordValue

@Composable
fun HomeScreen(
    onStartWorkout: (Long) -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenHistoryDetail: (Long) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onManageLocations: () -> Unit,
    onCreateSession: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current

    Scaffold { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding).statusBarsPadding())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Column(Modifier.statusBarsPadding()) {
                    Text(
                        Formatters.fullDate(System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Formatters.greeting() + (state.preferences.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    LocationSelectorChip(onManageLocations = onManageLocations)
                }
            }

            state.activeWorkout?.let { active ->
                item(key = "active") { ActiveWorkoutCard(active, viewModel::now, onResumeWorkout) }
            }

            item(key = "suggestion") {
                when {
                    !state.hasSessions -> EvoCard {
                        EmptyState(
                            icon = Icons.Rounded.FitnessCenter,
                            title = "Créez votre première séance",
                            message = "Choisissez vos exercices, vos séries et vos temps de repos : GymPulse s'occupe du reste.",
                            actionLabel = "Créer une séance",
                            onAction = onCreateSession,
                        )
                    }
                    state.suggestions.isNotEmpty() -> SuggestionCarousel(
                        state = state,
                        onStart = onStartWorkout,
                        onOpen = onOpenSession,
                        onOther = onOpenSessions,
                    )
                }
            }

            val dashboard = state.dashboard
            if (dashboard != null) {
                dashboard.lastWorkout?.let { last ->
                    item(key = "last") { LastWorkoutCard(last, unit, onClick = { onOpenHistoryDetail(last.id) }) }
                }
                item(key = "week") { WeekSummary(dashboard, state.preferences.weeklyGoal, unit) }
                item(key = "volume") {
                    VolumeCard(dashboard, state.period, unit, onPeriod = viewModel::setPeriod)
                }
                if (dashboard.recentRecords.isNotEmpty()) {
                    item(key = "records-header") {
                        SectionHeader("Records récents", actionLabel = "Tout voir", onAction = onOpenProgress)
                    }
                    item(key = "records") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(dashboard.recentRecords, key = { "${it.executionId}-${it.exerciseName}-${it.type}" }) { record ->
                                val gold = GymPulseTheme.extendedColors.record
                                EvoCard(
                                    modifier = Modifier.width(200.dp),
                                    onClick = record.exerciseId?.let { id -> { onOpenExercise(id) } },
                                    contentPadding = PaddingValues(16.dp),
                                ) {
                                    Icon(Icons.Rounded.MilitaryTech, null, tint = gold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(record.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                    Text(formatRecordValue(record, unit), style = MaterialTheme.typography.titleLarge, color = gold, fontWeight = FontWeight.ExtraBold)
                                    Text(Formatters.relativeDay(record.achievedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item(key = "frequency") { FrequencyCard(dashboard) }
                if (dashboard.topExercises.isNotEmpty()) {
                    item(key = "top-header") {
                        SectionHeader("Progression des principaux exercices", subtitle = "1RM estimé (indicatif) sur ${dashboard.period.longLabel.lowercase()}")
                    }
                    items(dashboard.topExercises, key = { "top-${it.exerciseId}-${it.exerciseName}" }) { trend ->
                        EvoCard(
                            onClick = trend.exerciseId?.let { id -> { onOpenExercise(id) } },
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(trend.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${trend.sessions} séances · " + (trend.lastValue?.let {
                                            if (trend.type == ExerciseType.DURATION) Formatters.seconds(it.toInt())
                                            else if (trend.series.isNotEmpty() && trend.lastValue > 0 && trend.type == ExerciseType.REPETITIONS) "~" + Formatters.weight(it, unit)
                                            else ""
                                        } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    trend.changeRatio?.let { TrendLabel(it, label = "sur la période") }
                                }
                                Sparkline(trend.series, Modifier.width(110.dp).height(44.dp), color = MaterialTheme.colorScheme.secondary)
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item(key = "see-all") {
                    TextButton(onClick = onOpenProgress, modifier = Modifier.fillMaxWidth()) { Text("Voir toute ma progression") }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutCard(active: WorkoutExecution, now: () -> Long, onResume: () -> Unit) {
    val time by rememberTicker(now, intervalMs = 1_000L)
    EvoCard(
        onClick = onResume,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(active.progress, size = 52.dp, strokeWidth = 5.dp) {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Séance en cours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(active.sessionName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "${active.completedSetCount}/${active.totalSets} séries · ${Formatters.clock(active.elapsedMs(time))}",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text("Reprendre", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * The suggestion of the day, followed by the other sessions that fit the location. The next card
 * peeks from the right edge and dots show the position; the "other session" button remains for
 * users who do not swipe (e.g. with a screen reader).
 */
@Composable
private fun SuggestionCarousel(state: HomeState, onStart: (Long) -> Unit, onOpen: (Long) -> Unit, onOther: () -> Unit) {
    val suggestions = state.suggestions
    val multiple = suggestions.size > 1
    val pagerState = rememberPagerState { suggestions.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = if (multiple) 36.dp else 0.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            // Every page is measured so the pager takes the tallest card's height and the cards line up.
            beyondViewportPageCount = suggestions.size,
            modifier = Modifier.semantics { if (multiple) stateDescription = "Séance ${pagerState.currentPage + 1} sur ${suggestions.size}" },
        ) { page ->
            val suggestion = suggestions[page]
            SuggestionCard(
                state = state,
                suggestion = suggestion,
                title = if (page == 0) "SÉANCE SUGGÉRÉE AUJOURD'HUI" else "AUSSI POSSIBLE ICI",
                onStart = { onStart(suggestion.session.id) },
                onOpen = { onOpen(suggestion.session.id) },
                onOther = onOther,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        if (multiple) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(suggestions.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    state: HomeState,
    suggestion: SessionSuggestion,
    title: String,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    onOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = suggestion.session
    GradientCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (session.isDemo) DemoBadge()
        }
        Spacer(Modifier.height(10.dp))
        Text(session.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            suggestion.reason.label + (suggestion.program?.let { " « ${it.name} »" } ?: "") +
                (suggestion.lastPerformedAt?.let { " · dernière fois ${Formatters.relativeDay(it).lowercase()}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(-10.dp)) {
            session.exercises.take(5).forEach {
                ExerciseImage(it.exercise.imageUri, size = 40.dp, shape = CircleShape)
            }
        }
        Spacer(Modifier.height(14.dp))
        // One badge per line: the card keeps the same layout whatever the screen width.
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalBadge(session.trainingGoal)
            Pill("${session.exercises.size} exercices · ${session.totalSets} séries", icon = Icons.Rounded.FitnessCenter)
            Pill("~${Formatters.duration(session.estimatedDurationSeconds * 1000L)}", icon = Icons.Rounded.Schedule)
            if (state.location.location != null) CompatibilityPill(suggestion.compatibility)
        }
        Spacer(Modifier.height(20.dp))
        BigActionButton("Démarrer l'entraînement", onClick = onStart, icon = Icons.Rounded.PlayArrow)
        TextButton(onClick = onOther, modifier = Modifier.fillMaxWidth()) { Text("Choisir une autre séance") }
    }
}

@Composable
private fun LastWorkoutCard(last: WorkoutExecution, unit: WeightUnit, onClick: () -> Unit) {
    EvoCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.SportsScore, null, tint = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Dernière séance · ${Formatters.relativeDay(last.startedAt)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(last.sessionName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.duration(last.durationMs)} · ${TrainingMath.completedSets(last)} séries · ${Formatters.volume(TrainingMath.workoutVolumeKg(last), unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (last.isDemo) DemoBadge()
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekSummary(dashboard: DashboardStats, weeklyGoal: Int, unit: WeightUnit) {
    val week = dashboard.thisWeek
    val last = dashboard.lastWeek
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Cette semaine")
        EvoCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = week.workouts.toFloat() / weeklyGoal.coerceAtLeast(1),
                    size = 76.dp,
                    strokeWidth = 8.dp,
                ) {
                    Text("${week.workouts}/$weeklyGoal", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            week.workouts >= weeklyGoal -> "Objectif hebdo atteint !"
                            week.workouts == 0 -> "C'est le moment de commencer"
                            else -> "Encore ${weeklyGoal - week.workouts} séance(s) pour l'objectif"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Semaine dernière : ${last.workouts} séance(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (week.workouts >= weeklyGoal) Icon(Icons.Rounded.EmojiEvents, null, tint = GymPulseTheme.extendedColors.record)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Durée",
                Formatters.duration(week.durationMs),
                Modifier.weight(1f),
                icon = Icons.Rounded.Schedule,
                accent = MaterialTheme.colorScheme.secondary,
                trend = ratio(week.durationMs.toDouble(), last.durationMs.toDouble()),
                trendLabel = "vs sem. -1",
            )
            StatTile(
                "Volume",
                Formatters.volume(week.volumeKg, unit),
                Modifier.weight(1f),
                icon = Icons.Rounded.FitnessCenter,
                trend = ratio(week.volumeKg, last.volumeKg),
                trendLabel = "vs sem. -1",
            )
        }
    }
}

private fun ratio(current: Double, previous: Double): Double? = if (previous > 0) (current - previous) / previous else null

private fun bucketLabel(granularity: Granularity, period: StatsPeriod): (Long) -> String = when {
    granularity == Granularity.DAY && period == StatsPeriod.WEEK -> Formatters::shortDay
    granularity == Granularity.MONTH -> Formatters::shortMonth
    else -> Formatters::dayMonth
}

@Composable
private fun VolumeCard(dashboard: DashboardStats, period: StatsPeriod, unit: WeightUnit, onPeriod: (StatsPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Évolution du volume", subtitle = "Charge × répétitions, ${period.longLabel.lowercase()}")
        PillSelector(StatsPeriod.entries, period, onPeriod, label = { it.label })
        EvoCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Formatters.volume(dashboard.periodTotals.volumeKg, unit),
                        style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers),
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${dashboard.periodTotals.workouts} séances · ${Formatters.hours(dashboard.periodTotals.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ratio(dashboard.periodTotals.volumeKg, dashboard.previousPeriodTotals.volumeKg)?.takeIf { period != StatsPeriod.ALL }?.let {
                    TrendLabel(it, label = "vs période préc.")
                }
            }
            Spacer(Modifier.height(12.dp))
            BarChart(
                points = dashboard.volumeSeries,
                valueFormatter = { Formatters.volume(it, unit) },
                axisFormatter = { Formatters.volumeCompact(it, unit) },
                dateFormatter = bucketLabel(dashboard.granularity, period),
                contentDescription = "Volume d'entraînement : ${Formatters.volume(dashboard.periodTotals.volumeKg, unit)} sur la période",
            )
        }
    }
}

@Composable
private fun FrequencyCard(dashboard: DashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Fréquence d'entraînement")
        EvoCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarToday, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${Formatters.number(dashboard.frequencyPerWeek)} séance(s) par semaine en moyenne",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            BarChart(
                points = dashboard.frequencySeries,
                valueFormatter = { "${it.toInt()} séance(s)" },
                axisFormatter = { "${it.toInt()}" },
                dateFormatter = bucketLabel(dashboard.frequencyGranularity, dashboard.period),
                contentDescription = "${dashboard.periodTotals.workouts} séances sur la période",
                color = MaterialTheme.colorScheme.tertiary,
                height = 160.dp,
            )
        }
    }
}
