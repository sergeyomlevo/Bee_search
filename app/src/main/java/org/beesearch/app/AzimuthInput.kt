package org.beesearch.app

internal data class ManualAzimuthValidation(
    val value: Int? = null,
    val errorMessage: String? = null,
)

internal fun validateManualAzimuth(input: String): ManualAzimuthValidation {
    val normalized = input.trim()
    if (normalized.isEmpty()) {
        return ManualAzimuthValidation(errorMessage = "Введите азимут")
    }

    val value = normalized.toIntOrNull()
        ?: return ManualAzimuthValidation(errorMessage = "Введите целое число от 0 до 359")
    if (value !in 0..359) {
        return ManualAzimuthValidation(errorMessage = "Азимут должен быть от 0° до 359°")
    }

    return ManualAzimuthValidation(value = value)
}

internal fun formatAzimuthValue(azimuthDeg: Double): String =
    if (azimuthDeg % 1.0 == 0.0) {
        azimuthDeg.toInt().toString()
    } else {
        azimuthDeg.toString().replace('.', ',')
    }

internal fun formatAzimuthAction(azimuthDeg: Double?): String =
    azimuthDeg?.let { "Азимут ${formatAzimuthValue(it)}°" } ?: "Азимут —"

internal fun azimuthInputText(azimuthDeg: Double?): String =
    azimuthDeg?.let(::formatAzimuthValue)?.replace(',', '.').orEmpty()
