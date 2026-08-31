package org.hikyaku.mobile.warehouse.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.create_shift_label_location_name
import hikyaku.sharedui.generated.resources.create_shift_label_search_address
import hikyaku.sharedui.generated.resources.warehouse_add_title
import hikyaku.sharedui.generated.resources.warehouse_submit
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.map.AddressAutocompleteField
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.Position

/**
 * Single-page add-warehouse form: a name and a geocoded address, the same starting-location
 * capture as the add-package form's inline warehouse section. Submits through
 * [AddWarehouseViewModel], which persists the row via [org.hikyaku.mobile.warehouse.WarehouseRepository].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWarehouseScreen(
    viewModel: AddWarehouseViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ToastEffect(state.error)

    AddWarehouseScreenContent(
        state = state,
        onSubmit = viewModel::submit,
        onCancel = onCancel,
        onSetName = viewModel::setName,
        onQueryChange = viewModel::onQueryChange,
        onPickAddress = viewModel::pickAddress,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWarehouseScreenContent(
    state: AddWarehouseUiState,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onSetName: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onPickAddress: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.warehouse_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.warehouse_submit))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = onSetName,
                label = { Text(stringResource(Res.string.create_shift_label_location_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AddressAutocompleteField(
                label = stringResource(Res.string.create_shift_label_search_address),
                query = state.query,
                suggestions = state.suggestions,
                searching = state.searching,
                hasSelection = state.picked != null,
                initialMapPosition = state.picked?.let { Position(longitude = it.lon, latitude = it.lat) },
                onQueryChange = onQueryChange,
                onPick = onPickAddress,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
private fun AddWarehouseScreenPreview() {
    HikyakuTheme {
        AddWarehouseScreenContent(
            state = AddWarehouseUiState(name = "Home"),
            onSubmit = {},
            onCancel = {},
            onSetName = {},
            onQueryChange = {},
            onPickAddress = {},
        )
    }
}
