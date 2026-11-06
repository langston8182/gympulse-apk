package com.gympulse.app.presentation.common

import com.gympulse.app.core.util.Formatters
import com.gympulse.app.domain.model.ExecutedSet
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.PerformedSet
import com.gympulse.app.domain.model.SetTarget
import com.gympulse.app.domain.model.WeightUnit

/** Text helpers shared by every screen that displays sets. */
object SetFormat {

    fun loadSuffix(loadMode: LoadMode): String = if (loadMode == LoadMode.PER_IMPLEMENT) " / haltère" else ""

    /** "12 × 20 kg", "12 reps", "45 s". */
    fun single(repetitions: Int?, weightKg: Double?, durationSeconds: Int?, type: ExerciseType, unit: WeightUnit): String =
        when {
            type == ExerciseType.DURATION -> Formatters.seconds(durationSeconds ?: 0)
            weightKg != null && weightKg > 0 -> "${repetitions ?: 0} × ${Formatters.weight(weightKg, unit)}"
            else -> "${repetitions ?: 0} reps"
        }

    fun performed(set: PerformedSet, type: ExerciseType, unit: WeightUnit): String =
        single(set.repetitions, set.weightKg, set.durationSeconds, type, unit)

    fun executedActual(set: ExecutedSet, type: ExerciseType, unit: WeightUnit): String =
        single(set.actualRepetitions, set.actualWeightKg, set.actualDurationSeconds, type, unit)

    fun executedTarget(set: ExecutedSet, type: ExerciseType, unit: WeightUnit): String = when {
        type == ExerciseType.DURATION -> Formatters.seconds(set.targetDurationSeconds ?: 0)
        set.targetRepetitions == null && set.targetWeightKg == null -> "Libre"
        else -> single(set.targetRepetitions, set.targetWeightKg, null, type, unit)
    }

    /** Compact summary of performed sets: "5 × 5 à 32 kg" or "12×20 · 8×24 · 5×28 kg". */
    fun performedSummary(sets: List<PerformedSet>, type: ExerciseType, unit: WeightUnit): String {
        if (sets.isEmpty()) return "—"
        if (type == ExerciseType.DURATION) return sets.joinToString(" · ") { Formatters.seconds(it.durationSeconds ?: 0) }
        val allSame = sets.all { it.repetitions == sets.first().repetitions && it.weightKg == sets.first().weightKg }
        val weighted = sets.any { (it.weightKg ?: 0.0) > 0 }
        return when {
            allSame && weighted -> "${sets.size} × ${sets.first().repetitions ?: 0} à ${Formatters.weight(sets.first().weightKg!!, unit)}"
            allSame -> "${sets.size} × ${sets.first().repetitions ?: 0} reps"
            weighted -> sets.joinToString(" · ") { "${it.repetitions ?: 0}×${it.weightKg?.let { w -> Formatters.weightValue(w, unit) } ?: "0"}" } + " ${unit.symbol}"
            else -> sets.joinToString(" · ") { "${it.repetitions ?: 0}" } + " reps"
        }
    }

    /** Planned sets: "4 × 10 · 22 kg", "12/8/5/5 reps · 20 → 32 kg", "3 × 45 s". */
    fun targetsSummary(sets: List<SetTarget>, type: ExerciseType, unit: WeightUnit): String {
        if (sets.isEmpty()) return "Aucune série"
        if (type == ExerciseType.DURATION) {
            val durations = sets.map { it.targetDurationSeconds ?: 0 }
            return if (durations.distinct().size == 1) "${sets.size} × ${Formatters.seconds(durations.first())}"
            else durations.joinToString(" / ") { Formatters.seconds(it) }
        }
        val reps = sets.map { it.targetRepetitions }
        val repsText = if (reps.distinct().size == 1) "${sets.size} × ${reps.first() ?: "—"}" else reps.joinToString("/") { it?.toString() ?: "—" } + " reps"
        val weights = sets.mapNotNull { it.targetWeightKg }.filter { it > 0 }
        val weightText = when {
            weights.isEmpty() -> ""
            weights.distinct().size == 1 -> " · ${Formatters.weight(weights.first(), unit)}"
            else -> " · ${Formatters.weightValue(weights.min(), unit)} → ${Formatters.weight(weights.max(), unit)}"
        }
        return repsText + weightText
    }
}
