package org.beesearch.app.ui.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.data.exchange.AreaMapCandidate
import org.beesearch.app.data.exchange.AreaMapDiscoveryResult
import org.beesearch.app.ui.map.CANCEL_LABEL
import org.beesearch.app.ui.map.CHOOSE_ANOTHER_AREA_MAP_LABEL
import org.beesearch.app.ui.map.LOAD_AREA_MAP_CONFIRM_LABEL
import org.beesearch.app.ui.map.OTHER_AREA_MAPS_LABEL

internal const val AREA_MAP_FOUND_DIALOG_TAG = "area-map-found-dialog"
internal const val AREA_MAP_ALTERNATIVES_DIALOG_TAG = "area-map-alternatives-dialog"
internal const val AREA_MAP_CHOOSE_ANOTHER_TAG = "area-map-choose-another"
internal const val AREA_MAP_CONFIRM_TAG = "area-map-confirm"
internal const val AREA_MAP_ALTERNATIVES_TAG = "area-map-alternatives"
internal const val AREA_MAP_CANDIDATE_TAG = "area-map-candidate"

/** What `Загрузить карту` should do after looking for a package of the current Ареал. */
internal sealed interface AreaMapLoadDecision {
    /** Offer the found package; the newest version first, with the others available. */
    data class OfferFound(
        val candidate: AreaMapCandidate,
        val alternatives: List<AreaMapCandidate>,
    ) : AreaMapLoadDecision

    /** Go straight to the standard Android file picker. */
    data object OpenPicker : AreaMapLoadDecision
}

/**
 * Turns a discovery result into the next user-visible step.
 *
 * Anything short of a usable found package - nothing there, an unclear pairing, or a platform that
 * will not let the app list the folder - leads to the ordinary file picker. None of those is an error
 * the user has to understand or fix.
 */
internal fun areaMapLoadDecision(result: AreaMapDiscoveryResult): AreaMapLoadDecision = when (result) {
    is AreaMapDiscoveryResult.One ->
        AreaMapLoadDecision.OfferFound(result.candidate, emptyList())

    is AreaMapDiscoveryResult.Several ->
        AreaMapLoadDecision.OfferFound(result.preferred, result.alternatives)

    is AreaMapDiscoveryResult.None,
    is AreaMapDiscoveryResult.Ambiguous,
    is AreaMapDiscoveryResult.Unavailable,
    -> AreaMapLoadDecision.OpenPicker
}

/** Title of the confirmation that offers a discovered map package. */
internal fun areaMapFoundTitle(areaName: String): String = "Найдена карта для ареала «$areaName»"

/**
 * Offers one discovered map package for the current Ареал.
 *
 * Nothing is imported until the user confirms: automatic discovery only shortens the search, it does
 * not decide for the user. The package still has to pass the existing validation afterwards, which is
 * why this dialog promises nothing about the map beyond its name.
 */
@Composable
internal fun AreaMapFoundDialog(
    areaName: String,
    candidate: AreaMapCandidate,
    hasAlternatives: Boolean,
    onLoad: () -> Unit,
    onShowAlternatives: () -> Unit,
    onChooseAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(AREA_MAP_FOUND_DIALOG_TAG),
        title = { Text(areaMapFoundTitle(areaName)) },
        // Every action is a stacked full-width button: at font_scale 1.7 a row of them would clip,
        // and a button that cannot be tapped is worse than no button at all.
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(AREA_MAP_CANDIDATE_TAG),
                )
                if (hasAlternatives) {
                    Text(
                        "Есть и другие версии карты для этого ареала.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onLoad,
                    modifier = Modifier.fillMaxWidth().testTag(AREA_MAP_CONFIRM_TAG),
                ) { Text(LOAD_AREA_MAP_CONFIRM_LABEL) }
                if (hasAlternatives) {
                    TextButton(
                        onClick = onShowAlternatives,
                        modifier = Modifier.fillMaxWidth().testTag(AREA_MAP_ALTERNATIVES_TAG),
                    ) { Text(OTHER_AREA_MAPS_LABEL) }
                } else {
                    TextButton(
                        onClick = onChooseAnother,
                        modifier = Modifier.fillMaxWidth().testTag(AREA_MAP_CHOOSE_ANOTHER_TAG),
                    ) { Text(CHOOSE_ANOTHER_AREA_MAP_LABEL) }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(CANCEL_LABEL) }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/**
 * The other versions found for the same Ареал, newest first.
 *
 * This is a short choice between already discovered packages, not a map manager: every entry is one
 * complete package of the current Ареал, and each still goes through the full import validation.
 */
@Composable
internal fun AreaMapAlternativesDialog(
    areaName: String,
    alternatives: List<AreaMapCandidate>,
    onPick: (AreaMapCandidate) -> Unit,
    onChooseAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(AREA_MAP_ALTERNATIVES_DIALOG_TAG),
        title = { Text("Другие карты ареала «$areaName»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                alternatives.forEach { candidate ->
                    TextButton(
                        onClick = { onPick(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(candidate.displayName) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseAnother) { Text(CHOOSE_ANOTHER_AREA_MAP_LABEL) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(CANCEL_LABEL) }
        },
    )
}
