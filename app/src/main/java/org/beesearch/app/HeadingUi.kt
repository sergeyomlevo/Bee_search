package org.beesearch.app

import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingState

internal fun formatAzimuthDegrees(azimuthDeg: Double): String =
    if (azimuthDeg % 1.0 == 0.0) {
        "${azimuthDeg.toInt()}°"
    } else {
        "${azimuthDeg.toString().replace('.', ',')}°"
    }

internal fun headingContentDescription(
    persistedAzimuth: Double?,
    headingState: HeadingState,
    isInFlight: Boolean = true,
    captureConsumed: Boolean = false,
): String = when {
    persistedAzimuth != null -> "Сохранённый азимут ${formatAzimuthDegrees(persistedAzimuth)}"
    !isInFlight -> "Азимут не зафиксирован. Пчела находится на точке"
    captureConsumed -> "Азимут удалён. Повторная фиксация для этого вылета недоступна"
    headingState is HeadingState.Available && headingState.accuracy == HeadingAccuracy.UNRELIABLE ->
        "Компас сообщает ненадёжное направление"
    headingState is HeadingState.Available && headingState.accuracy == HeadingAccuracy.LOW ->
        "Низкая точность компаса, текущее направление ${headingState.trueHeadingDeg} градусов"
    headingState is HeadingState.Available ->
        "Текущее направление ${headingState.trueHeadingDeg} градусов. Нажмите, чтобы сохранить"
    headingState is HeadingState.Unavailable -> headingState.message
    else -> "Ожидание направления телефона"
}
