package com.gympulse.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

@Immutable
data class ExerciseImagePreset(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val colors: List<Color>,
) {
    val uri: String get() = "$PRESET_SCHEME$key"
}

const val PRESET_SCHEME = "preset:"

/** Built-in illustrations that can be used instead of a photo. */
object ExerciseImagePresets {
    val all = listOf(
        ExerciseImagePreset("chest", "Pectoraux", Icons.Rounded.FitnessCenter, listOf(Color(0xFF3A5A00), Color(0xFF9BCB1B))),
        ExerciseImagePreset("back", "Dos", Icons.Rounded.Rowing, listOf(Color(0xFF003B57), Color(0xFF2BB7E8))),
        ExerciseImagePreset("shoulders", "Épaules", Icons.Rounded.SportsGymnastics, listOf(Color(0xFF4A1F7A), Color(0xFF9C8CFF))),
        ExerciseImagePreset("arms", "Bras", Icons.Rounded.SportsMartialArts, listOf(Color(0xFF6B1F00), Color(0xFFFF7A45))),
        ExerciseImagePreset("legs", "Jambes", Icons.AutoMirrored.Rounded.DirectionsRun, listOf(Color(0xFF00473A), Color(0xFF3DDC97))),
        ExerciseImagePreset("core", "Gainage", Icons.Rounded.SelfImprovement, listOf(Color(0xFF5C4400), Color(0xFFFFC94D))),
        ExerciseImagePreset("fullbody", "Corps entier", Icons.Rounded.AccessibilityNew, listOf(Color(0xFF5E0B2B), Color(0xFFFF5D8F))),
        ExerciseImagePreset("stretch", "Mobilité", Icons.Rounded.Spa, listOf(Color(0xFF123B3B), Color(0xFF6FD6C6))),
        ExerciseImagePreset("cardio", "Cardio", Icons.Rounded.MonitorHeart, listOf(Color(0xFF5A0D12), Color(0xFFFF5D6C))),
        ExerciseImagePreset("timer", "Chronométré", Icons.Rounded.Timer, listOf(Color(0xFF1F2A44), Color(0xFF7A8FBF))),
    )

    fun find(uri: String?): ExerciseImagePreset? =
        uri?.takeIf { it.startsWith(PRESET_SCHEME) }?.removePrefix(PRESET_SCHEME)?.let { key -> all.firstOrNull { it.key == key } }

    val fallback: ExerciseImagePreset get() = all.first()
}

/** Exercise picture: a gallery photo stored locally, or a built-in illustration. */
@Composable
fun ExerciseImage(
    imageUri: String?,
    modifier: Modifier = Modifier,
    size: Dp? = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    contentDescription: String? = null,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    val preset = ExerciseImagePresets.find(imageUri)
    if (imageUri != null && ExerciseArt.has(imageUri)) {
        ExerciseArtwork(imageUri, sized.clip(shape))
    } else if (imageUri == null || preset != null) {
        PresetArtwork(preset ?: ExerciseImagePresets.fallback, sized.clip(shape))
    } else {
        SubcomposeAsyncImage(
            model = imageUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = sized.clip(shape),
            loading = { PresetArtwork(ExerciseImagePresets.fallback, Modifier.fillMaxSize()) },
            error = { PresetArtwork(ExerciseImagePresets.fallback, Modifier.fillMaxSize()) },
        )
    }
}

@Composable
fun PresetArtwork(preset: ExerciseImagePreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Brush.linearGradient(preset.colors)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Color.White.copy(alpha = 0.08f)
            val step = size.minDimension / 4
            var x = -size.height
            while (x < size.width) {
                drawLine(stroke, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = step / 3)
                x += step
            }
        }
        Icon(
            preset.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}
