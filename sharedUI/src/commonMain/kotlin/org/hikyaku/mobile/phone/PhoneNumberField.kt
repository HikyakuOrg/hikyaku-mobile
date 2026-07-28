package org.hikyaku.mobile.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_search
import hikyaku.sharedui.generated.resources.cd_country_selector
import hikyaku.sharedui.generated.resources.phone_error_invalid
import hikyaku.sharedui.generated.resources.phone_label_default
import hikyaku.sharedui.generated.resources.phone_select_country_title
import kotlinx.coroutines.launch
import org.hikyaku.mobile.phone.model.Country
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A phone-number input: a country picker (flag + dial code) sitting as the leading icon of an
 * [OutlinedTextField] into which the user types the national number. Validation and E.164 output
 * are handled by the caller via [PhoneNumbers]; this field just surfaces an inline error when the
 * typed number isn't valid for the selected country.
 *
 * State is hoisted: [nationalNumber] and [countryIso] come in, edits go out through
 * [onNationalNumberChange] / [onCountrySelected]. Adapted from FirebaseUI-Android's phone-auth UI
 * (Apache-2.0), stripped of its auth coupling.
 */
@Composable
fun PhoneNumberField(
    nationalNumber: String,
    countryIso: String,
    onNationalNumberChange: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = stringResource(Res.string.phone_label_default),
) {
    val country = remember(countryIso) { PhoneNumbers.countryFor(countryIso) }
    val invalid = remember(nationalNumber, countryIso) {
        nationalNumber.isNotBlank() && !PhoneNumbers.isValid(nationalNumber, countryIso)
    }
    OutlinedTextField(
        value = nationalNumber,
        // Keep just digits and spacing; libphonenumber tolerates spaces and strips trunk prefixes.
        onValueChange = { input -> onNationalNumberChange(input.filter { it.isDigit() || it == ' ' }) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(Res.string.phone_error_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        leadingIcon = {
            CountrySelector(selected = country, enabled = enabled, onCountrySelected = onCountrySelected)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun PhoneNumberFieldPreview() {
    HikyakuTheme {
        PhoneNumberField(
            nationalNumber = "91234567",
            countryIso = "SG",
            onNationalNumberChange = {},
            onCountrySelected = {},
        )
    }
}

/**
 * The compact `🇦🇺 +61 ▾` row shown inside the phone field. Tapping it opens a searchable
 * [ModalBottomSheet] of every country; picking one emits its ISO code and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySelector(
    selected: Country,
    enabled: Boolean,
    onCountrySelected: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val countrySelectorDescription = stringResource(Res.string.cd_country_selector)

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(enabled = enabled) { showSheet = true }
            .padding(start = 12.dp, end = 4.dp)
            .semantics {
                role = Role.DropdownList
                contentDescription = countrySelectorDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(selected.flag, style = MaterialTheme.typography.bodyLarge)
        Text(selected.dialCode, style = MaterialTheme.typography.bodyLarge)
        Text("▾", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showSheet) {
        val all = remember { PhoneNumbers.countries() }
        val filtered = remember(query, all) {
            val q = query.trim()
            if (q.isEmpty()) {
                all
            } else {
                all.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.dialCode.contains(q) ||
                        it.iso.equals(q, ignoreCase = true)
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                query = ""
            },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    stringResource(Res.string.phone_select_country_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                    items(filtered, key = { it.iso }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCountrySelected(c.iso)
                                    query = ""
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        if (!sheetState.isVisible) showSheet = false
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(c.flag, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                c.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                c.dialCode,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
