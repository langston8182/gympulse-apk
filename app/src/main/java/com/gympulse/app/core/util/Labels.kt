package com.gympulse.app.core.util

import com.gympulse.app.domain.location.Compatibility
import com.gympulse.app.domain.location.SuggestionReason
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.ExerciseType
import com.gympulse.app.domain.model.LoadMode
import com.gympulse.app.domain.model.LocationType
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.RecordType
import com.gympulse.app.domain.model.StatsPeriod
import com.gympulse.app.domain.model.ThemeMode
import com.gympulse.app.domain.model.TrainingSplit
import com.gympulse.app.domain.model.TrainingGoal
import com.gympulse.app.domain.stats.ExerciseMetric

val MuscleGroup.label: String
    get() = when (this) {
        MuscleGroup.CHEST -> "Pectoraux"
        MuscleGroup.BACK -> "Dos"
        MuscleGroup.SHOULDERS -> "Épaules"
        MuscleGroup.BICEPS -> "Biceps"
        MuscleGroup.TRICEPS -> "Triceps"
        MuscleGroup.FOREARMS -> "Avant-bras"
        MuscleGroup.TRAPS -> "Trapèzes"
        MuscleGroup.LOWER_BACK -> "Lombaires"
        MuscleGroup.ABS -> "Abdominaux"
        MuscleGroup.OBLIQUES -> "Obliques"
        MuscleGroup.QUADRICEPS -> "Quadriceps"
        MuscleGroup.HAMSTRINGS -> "Ischio-jambiers"
        MuscleGroup.GLUTES -> "Fessiers"
        MuscleGroup.CALVES -> "Mollets"
        MuscleGroup.ADDUCTORS -> "Adducteurs"
        MuscleGroup.FULL_BODY -> "Corps entier"
    }

val Equipment.label: String
    get() = when (this) {
        Equipment.BODYWEIGHT -> "Poids du corps"
        Equipment.DUMBBELL -> "Haltères"
        Equipment.BARBELL -> "Barre olympique"
        Equipment.EZ_BAR -> "Barre EZ"
        Equipment.KETTLEBELL -> "Kettlebell"
        Equipment.BENCH -> "Banc"
        Equipment.SQUAT_RACK -> "Rack à squat"
        Equipment.SMITH_MACHINE -> "Smith machine"
        Equipment.CABLE -> "Poulie"
        Equipment.MACHINE -> "Machine guidée"
        Equipment.LEG_PRESS -> "Presse à cuisses"
        Equipment.PULL_UP_BAR -> "Barre de traction"
        Equipment.DIP_STATION -> "Station de dips"
        Equipment.RESISTANCE_BAND -> "Élastiques"
        Equipment.MAT -> "Tapis"
    }

val TrainingGoal.label: String
    get() = when (this) {
        TrainingGoal.HYPERTROPHY -> "Hypertrophie"
        TrainingGoal.STRENGTH_HYPERTROPHY -> "Force-hypertrophie"
        TrainingGoal.STRENGTH -> "Force"
        TrainingGoal.ENDURANCE -> "Endurance"
        TrainingGoal.CUSTOM -> "Personnalisé"
    }

val ExerciseType.label: String
    get() = when (this) {
        ExerciseType.REPETITIONS -> "Répétitions"
        ExerciseType.DURATION -> "Durée"
    }

val LoadMode.label: String
    get() = when (this) {
        LoadMode.TOTAL -> "Charge totale"
        LoadMode.PER_IMPLEMENT -> "Par haltère"
    }

val LocationType.label: String
    get() = when (this) {
        LocationType.HOME -> "Maison"
        LocationType.GYM -> "Salle de sport"
        LocationType.WORK -> "Travail"
        LocationType.OTHER -> "Autre"
    }

val StatsPeriod.label: String
    get() = when (this) {
        StatsPeriod.WEEK -> "7 j"
        StatsPeriod.MONTH -> "30 j"
        StatsPeriod.QUARTER -> "3 mois"
        StatsPeriod.HALF_YEAR -> "6 mois"
        StatsPeriod.YEAR -> "1 an"
        StatsPeriod.ALL -> "Tout"
    }

val StatsPeriod.longLabel: String
    get() = when (this) {
        StatsPeriod.WEEK -> "7 derniers jours"
        StatsPeriod.MONTH -> "30 derniers jours"
        StatsPeriod.QUARTER -> "3 derniers mois"
        StatsPeriod.HALF_YEAR -> "6 derniers mois"
        StatsPeriod.YEAR -> "12 derniers mois"
        StatsPeriod.ALL -> "Depuis le début"
    }

val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Système"
        ThemeMode.DARK -> "Sombre"
        ThemeMode.LIGHT -> "Clair"
    }

val RecordType.label: String
    get() = when (this) {
        RecordType.MAX_WEIGHT -> "Charge max"
        RecordType.ESTIMATED_ONE_REP_MAX -> "1RM estimé"
        RecordType.BEST_SET_VOLUME -> "Meilleure série"
        RecordType.SESSION_VOLUME -> "Volume"
        RecordType.MAX_REPETITIONS -> "Répétitions max"
        RecordType.MAX_DURATION -> "Durée max"
    }

val ExerciseMetric.label: String
    get() = when (this) {
        ExerciseMetric.ESTIMATED_ONE_REP_MAX -> "1RM estimé"
        ExerciseMetric.MAX_WEIGHT -> "Charge max"
        ExerciseMetric.WEIGHT_AT_REPS -> "Charge à reps égales"
        ExerciseMetric.VOLUME -> "Volume"
        ExerciseMetric.REPETITIONS -> "Répétitions"
        ExerciseMetric.BEST_DURATION -> "Meilleure durée"
        ExerciseMetric.TOTAL_DURATION -> "Durée totale"
    }

val Compatibility.label: String
    get() = when (this) {
        Compatibility.LINKED -> "Associée à ce lieu"
        Compatibility.EQUIPMENT_MATCH -> "Matériel disponible"
        Compatibility.UNKNOWN -> "Lieu inconnu"
        Compatibility.INCOMPATIBLE -> "Matériel manquant"
    }

val TrainingSplit.label: String
    get() = when (this) {
        TrainingSplit.PUSH -> "Push"
        TrainingSplit.PULL -> "Pull"
        TrainingSplit.LEGS -> "Legs"
        TrainingSplit.OTHER -> "Autres"
    }

val SuggestionReason.label: String
    get() = when (this) {
        SuggestionReason.NEXT_IN_PROGRAM -> "Suite de votre programme"
        SuggestionReason.PROGRAM_START -> "Début de programme"
        SuggestionReason.LOCATION_MATCH -> "Idéale pour ce lieu"
        SuggestionReason.EQUIPMENT_MATCH -> "Compatible avec le matériel du lieu"
        SuggestionReason.LEAST_RECENT -> "Pas faite depuis longtemps"
    }
