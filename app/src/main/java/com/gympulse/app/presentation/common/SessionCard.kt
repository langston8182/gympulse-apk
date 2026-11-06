package com.gympulse.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gympulse.app.core.designsystem.components.DemoBadge
import com.gympulse.app.core.designsystem.components.EvoCard
import com.gympulse.app.core.designsystem.components.ExerciseImage
import com.gympulse.app.core.designsystem.components.GoalBadge
import com.gympulse.app.core.designsystem.components.Pill
import com.gympulse.app.core.designsystem.theme.GymPulseTheme
import com.gympulse.app.core.util.Formatters
import com.gympulse.app.core.util.label
import com.gympulse.app.domain.location.Compatibility
import com.gympulse.app.domain.model.WorkoutSession

/** Session summary card used by the sessions list, programs and the dashboard. */
@Composable
fun SessionCard(
    session: WorkoutSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compatibility: Compatibility? = null,
    lastPerformedAt: Long? = null,
    onStart: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    leadingLabel: String? = null,
) {
    EvoCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(68.dp).height(56.dp)) {
                session.exercises.take(3).forEachIndexed { index, item ->
                    ExerciseImage(
                        item.exercise.imageUri,
                        size = 40.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.offset(x = (index * 13).dp, y = (index * 7).dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (leadingLabel != null) {
                    Text(leadingLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(session.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoalBadge(session.trainingGoal)
                    if (session.isDemo) DemoBadge()
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${session.exercises.size} exercices · ${session.totalSets} séries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "~${Formatters.duration(session.estimatedDurationSeconds * 1000L)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lastPerformedAt != null || (compatibility != null && compatibility != Compatibility.UNKNOWN)) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (compatibility != null && compatibility != Compatibility.UNKNOWN) CompatibilityPill(compatibility)
                        if (lastPerformedAt != null) Pill(Formatters.relativeDay(lastPerformedAt), icon = Icons.Rounded.History)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (session.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (session.isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (session.isFavorite) GymPulseTheme.extendedColors.record else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onStart != null) {
                    FilledIconButton(
                        onClick = onStart,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Démarrer ${session.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun CompatibilityPill(compatibility: Compatibility, modifier: Modifier = Modifier) {
    val (color, icon) = when (compatibility) {
        Compatibility.LINKED, Compatibility.EQUIPMENT_MATCH -> GymPulseTheme.extendedColors.success to Icons.Rounded.CheckCircle
        Compatibility.INCOMPATIBLE -> GymPulseTheme.extendedColors.warning to Icons.Rounded.Warning
        Compatibility.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Rounded.CheckCircle
    }
    Pill(compatibility.label, modifier = modifier, icon = icon, containerColor = color.copy(alpha = 0.14f), contentColor = color)
}
