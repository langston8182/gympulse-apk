package com.gympulse.app.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gympulse.app.BuildConfig
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.IconBadge
import com.gympulse.app.core.designsystem.components.ListRow
import com.gympulse.app.core.designsystem.components.LocalWeightUnit
import com.gympulse.app.core.designsystem.components.ProgressRing
import com.gympulse.app.core.designsystem.components.StatTile
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.designsystem.theme.TabularNumbers
import com.gympulse.app.core.time.TimeProvider
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.UserPreferences
import com.gympulse.app.domain.model.WorkoutTotals
import com.gympulse.app.domain.repository.PreferencesRepository
import com.gympulse.app.domain.repository.WorkoutRepository
import com.gympulse.app.domain.stats.StatsCalculator
import com.gympulse.app.domain.stats.TrainingMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class ProfileState(
    val loading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val totals: WorkoutTotals = WorkoutTotals(),
    val since: Long? = null,
    val streakWeeks: Int = 0,
    val thisWeek: Int = 0,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    workoutRepository: WorkoutRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val state: StateFlow<ProfileState> = combine(preferencesRepository.preferences, workoutRepository.observeHistory()) { prefs, history ->
        val zone = ZoneId.systemDefault()
        val now = time.now()
        val thisWeek = StatsCalculator.weekRange(now, zone)
        val workoutsThisWeek = history.count { it.startedAt in thisWeek }
        var weeksAgo = if (workoutsThisWeek > 0) 0L else 1L
        var streak = 0
        while (true) {
            val range = StatsCalculator.weekRange(now, zone, weeksAgo)
            if (history.none { it.startedAt in range }) break
            streak++
            weeksAgo++
        }
        ProfileState(
            loading = false,
            preferences = prefs,
            totals = TrainingMath.totals(history),
            since = history.minOfOrNull { it.startedAt },
            streakWeeks = streak,
            thisWeek = workoutsThisWeek,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun setName(name: String) {
        viewModelScope.launch { preferencesRepository.setUserName(name) }
    }

    fun setWeeklyGoal(goal: Int) {
        viewModelScope.launch { preferencesRepository.setWeeklyGoal(goal) }
    }
}

@Composable
fun ProfileScreen(
    onOpenExerciseLibrary: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenEquipment: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    var editName by remember { mutableStateOf(false) }
    val name = state.preferences.userName
    val goal = state.preferences.weeklyGoal

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
                    if (initials.isNotEmpty()) {
                        Text(initials, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    } else {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "Athlète GymPulse" }, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.since?.let { "Membre depuis le ${Formatters.dayMonthYear(it)}" } ?: "Prêt pour la première séance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Modifier le prénom") }
            }

            EvoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress = state.thisWeek.toFloat() / goal.coerceAtLeast(1), size = 84.dp, strokeWidth = 9.dp) {
                        Text("${state.thisWeek}/$goal", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Objectif de la semaine", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = GymPulseTheme.extendedColors.record, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (state.streakWeeks > 0) "${state.streakWeeks} semaine(s) d'affilée" else "Aucune série en cours",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(onClick = { viewModel.setWeeklyGoal(goal + 1) }, enabled = goal < 14) {
                            Icon(Icons.Rounded.Add, contentDescription = "Augmenter l'objectif")
                        }
                        FilledTonalIconButton(onClick = { viewModel.setWeeklyGoal(goal - 1) }, enabled = goal > 1) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Diminuer l'objectif")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Séances", Formatters.integer(state.totals.workouts), Modifier.weight(1f), icon = Icons.Rounded.CalendarToday)
                StatTile("Volume total", Formatters.volume(state.totals.volumeKg, unit), Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter, accent = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Temps total", Formatters.hours(state.totals.durationMs), Modifier.weight(1f), icon = Icons.Rounded.Schedule, accent = MaterialTheme.colorScheme.secondary)
                StatTile("Séries", Formatters.integer(state.totals.sets), Modifier.weight(1f), icon = Icons.Rounded.Insights, accent = GymPulseTheme.extendedColors.chart[3])
            }

            EvoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                NavRow("Bibliothèque d'exercices", "Créer et modifier vos exercices", Icons.AutoMirrored.Rounded.MenuBook, onOpenExerciseLibrary)
                NavRow("Lieux d'entraînement", "Maison, salle, travail…", Icons.Rounded.Place, onOpenLocations)
                NavRow("Matériel", "Équipement disponible par lieu", Icons.Rounded.FitnessCenter, onOpenEquipment)
                NavRow("Progression", "Statistiques et historique", Icons.Rounded.Insights, onOpenProgress)
                NavRow("Paramètres", "Thème, unités, sons, données", Icons.Rounded.Settings, onOpenSettings)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "GymPulse ${BuildConfig.VERSION_NAME} · données synchronisées",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (editName) {
        var value by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Votre prénom") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(40) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setName(value)
                    editName = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { IconBadge(icon, size = 40.dp) },
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}
