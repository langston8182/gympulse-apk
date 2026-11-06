package com.gympulse.app.domain.model

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb");

    fun fromKg(kg: Double): Double = if (this == KG) kg else kg / KG_PER_LB

    fun toKg(value: Double): Double = if (this == KG) value else value * KG_PER_LB

    /** Default increment used by the +/- weight steppers, expressed in this unit. */
    val step: Double get() = if (this == KG) 1.0 else 2.5

    companion object {
        const val KG_PER_LB = 0.45359237
    }
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val weeklyGoal: Int = 3,
    val autoDetectLocation: Boolean = true,
    val manualLocationId: Long? = null,
)
