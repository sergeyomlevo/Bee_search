package org.beesearch.app.ui.physicalobjects

import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties

/** Editable strings deliberately remain separate from persisted numeric values. */
data class PhysicalObjectFormState(
    val tree: String = "",
    val entranceHeightCm: String = "",
    val azimuthDeg: String = "",
    val outerDiameterCm: String = "",
    val internalDiameterCm: String = "",
    val material: String = "",
    val internalHeightCm: String = "",
    val notes: String = "",
    val fixedAzimuthDeg: Int? = null,
    val errors: Map<String, String> = emptyMap(),
)

data class PhysicalObjectFormValidation(
    val errors: Map<String, String>,
    val hollow: HollowProperties? = null,
    val logHive: LogHiveProperties? = null,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

fun PhysicalObjectFormState.validateHollow(): PhysicalObjectFormValidation {
    val errors = linkedMapOf<String, String>()
    val treeValue = tree.trim()
    if (treeValue.isEmpty()) errors["tree"] = "Укажите дерево"
    val height = positive(entranceHeightCm, "entranceHeightCm", errors)
    val azimuth = azimuth(azimuthDeg, errors)
    val outer = positive(outerDiameterCm, "outerDiameterCm", errors)
    val inner = optionalPositive(internalDiameterCm, "internalDiameterCm", errors)
    if (errors.isNotEmpty()) return PhysicalObjectFormValidation(errors)
    return PhysicalObjectFormValidation(
        errors = emptyMap(),
        hollow = HollowProperties(treeValue, height!!, azimuth!!, outer!!, inner, notes.trim().ifEmpty { null }),
    )
}

fun PhysicalObjectFormState.validateLogHive(): PhysicalObjectFormValidation {
    val errors = linkedMapOf<String, String>()
    val treeValue = tree.trim()
    if (treeValue.isEmpty()) errors["tree"] = "Укажите дерево"
    val height = positive(entranceHeightCm, "entranceHeightCm", errors)
    val azimuth = azimuth(azimuthDeg, errors)
    val outer = positive(outerDiameterCm, "outerDiameterCm", errors)
    val materialValue = material.trim()
    if (materialValue.isEmpty()) errors["material"] = "Укажите материал"
    val inner = positive(internalDiameterCm, "internalDiameterCm", errors)
    val innerHeight = positive(internalHeightCm, "internalHeightCm", errors)
    if (errors.isNotEmpty()) return PhysicalObjectFormValidation(errors)
    return PhysicalObjectFormValidation(
        errors = emptyMap(),
        logHive = LogHiveProperties(
            treeValue, height!!, azimuth!!, outer!!, materialValue, inner!!, innerHeight!!,
            notes.trim().ifEmpty { null },
        ),
    )
}

private fun positive(raw: String, key: String, errors: MutableMap<String, String>): Double? {
    val value = raw.trim().replace(',', '.').toDoubleOrNull()
    if (value == null || !value.isFinite() || value <= 0.0) errors[key] = "Введите положительное число"
    return value
}

private fun optionalPositive(raw: String, key: String, errors: MutableMap<String, String>): Double? {
    if (raw.trim().isEmpty()) return null
    return positive(raw, key, errors)
}

private fun azimuth(raw: String, errors: MutableMap<String, String>): Int? {
    val value = raw.trim().toIntOrNull()
    if (value == null || value !in 0..359) errors["azimuthDeg"] = "Введите целое число от 0 до 359°"
    return value
}
