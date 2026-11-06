package com.gympulse.app.data.local.db

import androidx.room.TypeConverter
import com.gympulse.app.domain.model.Equipment
import com.gympulse.app.domain.model.MuscleGroup
import com.gympulse.app.domain.model.SetTarget
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @TypeConverter
    fun muscleGroupsToString(value: List<MuscleGroup>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscleGroups(value: String): List<MuscleGroup> =
        value.split(',').filter { it.isNotBlank() }.mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun equipmentToString(value: List<Equipment>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToEquipment(value: String): List<Equipment> =
        value.split(',').filter { it.isNotBlank() }.mapNotNull { name -> Equipment.entries.firstOrNull { it.name == name } }

    @TypeConverter
    fun setTargetsToString(value: List<SetTarget>): String = json.encodeToString(value)

    @TypeConverter
    fun stringToSetTargets(value: String): List<SetTarget> =
        runCatching { json.decodeFromString<List<SetTarget>>(value) }.getOrDefault(emptyList())
}
