package org.hikyaku.mobile.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.address_autocomplete_no_results
import hikyaku.sharedui.generated.resources.address_building_escalation_dismiss
import hikyaku.sharedui.generated.resources.address_building_escalation_message
import hikyaku.sharedui.generated.resources.address_pick_on_map
import hikyaku.sharedui.generated.resources.address_unit_label
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.geocode.model.isLikelyBuilding
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.Position

/** Minimum characters typed before an autocomplete search fires, mirroring every geocode-backed ViewModel's debounce threshold. */
const val ADDRESS_AUTOCOMPLETE_MIN_QUERY_LENGTH = 3

/**
 * A text field backed by [org.hikyaku.mobile.geocode.GeocodeRepository] autocomplete: typing shows
 * matching [suggestions] below, and a "Pick on map" action opens [LocationPickerDialog] as a
 * fallback for an address the search can't find. Shared by every form that captures a geocoded
 * address (a package's sender/receiver/warehouse, the standalone warehouse form).
 *
 * [picked] doubles as "has a selection been made" and, when [onUnitChange] is supplied, as the
 * subject of the unit/building-escalation prompt (HIK-53): once an address is picked, a "Unit,
 * suite, or business name" field appears, always optional. If [picked] looks like a building
 * ([isLikelyBuilding]) and [buildingEscalationDismissed] is false, the unit field autofocuses and
 * supporting copy names the building — a soft prompt, never a hard gate; [onDismissBuildingEscalation]
 * is the "deliver to the main entrance instead" escape. Callers that don't pass [onUnitChange] (the
 * warehouse pickers — there's no column to persist a warehouse's unit into) get no unit UI at all.
 */
@Composable
fun AddressAutocompleteField(
    label: String,
    query: String,
    suggestions: List<AddressSuggestion>,
    searching: Boolean,
    picked: AddressSuggestion?,
    initialMapPosition: Position?,
    onQueryChange: (String) -> Unit,
    onPick: (AddressSuggestion) -> Unit,
    maxSuggestions: Int = 8,
    unit: String = "",
    onUnitChange: ((String) -> Unit)? = null,
    buildingEscalationDismissed: Boolean = false,
    onDismissBuildingEscalation: (() -> Unit)? = null,
) {
    var showLocationPicker by remember { mutableStateOf(false) }
    val hasSelection = picked != null
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = if (searching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showLocationPicker = true }) {
            Icon(LocationPinIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.address_pick_on_map))
        }
        if (picked != null && onUnitChange != null) {
            val escalating = isLikelyBuilding(picked) && !buildingEscalationDismissed
            val unitFocusRequester = remember { FocusRequester() }
            LaunchedEffect(picked.gid, picked.label) {
                if (escalating) unitFocusRequester.requestFocus()
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = unit,
                onValueChange = onUnitChange,
                label = { Text(stringResource(Res.string.address_unit_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(unitFocusRequester),
            )
            if (escalating) {
                Text(
                    text = stringResource(
                        Res.string.address_building_escalation_message,
                        picked.street ?: picked.label,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                onDismissBuildingEscalation?.let { dismiss ->
                    TextButton(onClick = dismiss) {
                        Text(stringResource(Res.string.address_building_escalation_dismiss))
                    }
                }
            }
        }
        if (!searching && !hasSelection && suggestions.isEmpty() && query.trim().length >= ADDRESS_AUTOCOMPLETE_MIN_QUERY_LENGTH) {
            Text(
                text = stringResource(Res.string.address_autocomplete_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            )
        } else {
            suggestions.take(maxSuggestions).forEach { s ->
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp, horizontal = 4.dp),
                )
                HorizontalDivider()
            }
        }
    }
    if (showLocationPicker) {
        LocationPickerDialog(
            initialPosition = initialMapPosition,
            onDismiss = { showLocationPicker = false },
            onConfirm = {
                onPick(it)
                showLocationPicker = false
            },
        )
    }
}
