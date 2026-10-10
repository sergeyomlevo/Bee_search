package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.CountRange
import org.beesearch.app.domain.model.MeasurementRange

private val RANGE_INSET = 16.dp

/** Compact inclusive min/max editor. Empty text is an open bound. */
@Composable
internal fun MapCountRangeEditor(
    type: MapDataType,
    criterion: String,
    range: CountRange,
    onChange: (CountRange) -> Unit,
    resetToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    MapNumericRangeEditor(
        type = type,
        criterion = criterion,
        minText = range.min?.toString().orEmpty(),
        maxText = range.max?.toString().orEmpty(),
        keyboardType = KeyboardType.Number,
        parse = { it.toIntOrNull()?.takeIf { value -> value >= 0 } },
        onChange = { min, max -> onChange(CountRange(min, max)) },
        resetToken = resetToken,
        modifier = modifier,
    )
}

@Composable
internal fun MapMeasurementRangeEditor(
    type: MapDataType,
    criterion: String,
    range: MeasurementRange,
    onChange: (MeasurementRange) -> Unit,
    resetToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    MapNumericRangeEditor(
        type = type,
        criterion = criterion,
        minText = range.min?.let(::numberText).orEmpty(),
        maxText = range.max?.let(::numberText).orEmpty(),
        keyboardType = KeyboardType.Decimal,
        parse = { text ->
            text.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0.0 }
        },
        onChange = { min, max -> onChange(MeasurementRange(min, max)) },
        resetToken = resetToken,
        modifier = modifier,
    )
}

@Composable
private fun <T : Comparable<T>> MapNumericRangeEditor(
    type: MapDataType,
    criterion: String,
    minText: String,
    maxText: String,
    keyboardType: KeyboardType,
    parse: (String) -> T?,
    onChange: (T?, T?) -> Unit,
    resetToken: Int,
    modifier: Modifier = Modifier,
) {
    var minDraft by remember(type, criterion, resetToken) { mutableStateOf(minText) }
    var maxDraft by remember(type, criterion, resetToken) { mutableStateOf(maxText) }
    var minError by remember(type, criterion, resetToken) { mutableStateOf(false) }
    var maxError by remember(type, criterion, resetToken) { mutableStateOf(false) }
    var orderError by remember(type, criterion, resetToken) { mutableStateOf(false) }

    // External reset or restored state updates the draft when the persisted valid value changes.
    // An invalid temporary draft does not change persisted state, so it remains visible until the
    // user fixes it or resets this criterion.
    LaunchedEffect(minText, maxText) {
        if (minDraft != minText) minDraft = minText
        if (maxDraft != maxText) maxDraft = maxText
        if (minDraft == minText) minError = false
        if (maxDraft == maxText) maxError = false
        if (minDraft == minText && maxDraft == maxText) orderError = false
    }

    fun apply(minValue: String, maxValue: String) {
        val min = if (minValue.isEmpty()) null else parse(minValue)
        val max = if (maxValue.isEmpty()) null else parse(maxValue)
        minError = minValue.isNotEmpty() && min == null
        maxError = maxValue.isNotEmpty() && max == null
        orderError = !minError && !maxError && min != null && max != null && min > max
        if (!minError && !maxError && !orderError) onChange(min, max)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = RANGE_INSET, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RangeField(
                value = minDraft,
                onValueChange = { minDraft = it; apply(it, maxDraft) },
                label = "От",
                isError = minError || orderError,
                keyboardType = keyboardType,
                modifier = Modifier.weight(1f).testTag("filter-$criterion-min-${type.name}"),
            )
            RangeField(
                value = maxDraft,
                onValueChange = { maxDraft = it; apply(minDraft, it) },
                label = "До",
                isError = maxError || orderError,
                keyboardType = keyboardType,
                modifier = Modifier.weight(1f).testTag("filter-$criterion-max-${type.name}"),
            )
        }
        if (minError) Text("Введите неотрицательное число", modifier = Modifier.testTag("filter-$criterion-error-${type.name}"))
        else if (maxError) Text("Введите неотрицательное число", modifier = Modifier.testTag("filter-$criterion-error-${type.name}"))
        else if (orderError) Text("Минимум не может быть больше максимума", modifier = Modifier.testTag("filter-$criterion-error-${type.name}"))
    }
}

@Composable
private fun RangeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Keep the field textual while editing, but reject characters that cannot represent
            // either supported numeric value. Empty text remains the open-bound representation.
            if (newValue.isEmpty() || newValue.matches(Regex("-?[0-9]*([.,][0-9]*)?"))) onValueChange(newValue)
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.heightIn(min = 56.dp),
    )
}

private fun numberText(value: Double): String = value.toString().removeSuffix(".0")
