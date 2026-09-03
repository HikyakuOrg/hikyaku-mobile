package org.hikyaku.mobile.packages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.action_delete
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.action_share
import hikyaku.sharedui.generated.resources.cd_package_photo
import hikyaku.sharedui.generated.resources.cd_package_qr_code
import hikyaku.sharedui.generated.resources.package_detail_actual_arrival
import hikyaku.sharedui.generated.resources.package_detail_actual_departure
import hikyaku.sharedui.generated.resources.package_detail_created
import hikyaku.sharedui.generated.resources.package_detail_delete_confirm_message
import hikyaku.sharedui.generated.resources.package_detail_delete_confirm_title
import hikyaku.sharedui.generated.resources.package_detail_delete_error
import hikyaku.sharedui.generated.resources.package_detail_dimensions_missing
import hikyaku.sharedui.generated.resources.package_detail_from
import hikyaku.sharedui.generated.resources.package_detail_label_size
import hikyaku.sharedui.generated.resources.package_detail_label_volume
import hikyaku.sharedui.generated.resources.package_detail_label_weight
import hikyaku.sharedui.generated.resources.package_detail_no_address
import hikyaku.sharedui.generated.resources.package_detail_no_name
import hikyaku.sharedui.generated.resources.package_detail_no_timeline
import hikyaku.sharedui.generated.resources.package_detail_pending
import hikyaku.sharedui.generated.resources.package_detail_print_label
import hikyaku.sharedui.generated.resources.package_detail_scheduled_arrival
import hikyaku.sharedui.generated.resources.package_detail_scheduled_departure
import hikyaku.sharedui.generated.resources.package_detail_section_dimensions
import hikyaku.sharedui.generated.resources.package_detail_section_journey
import hikyaku.sharedui.generated.resources.package_detail_section_notes
import hikyaku.sharedui.generated.resources.package_detail_section_origin
import hikyaku.sharedui.generated.resources.package_detail_section_pod
import hikyaku.sharedui.generated.resources.package_detail_section_schedule
import hikyaku.sharedui.generated.resources.package_detail_section_timeline
import hikyaku.sharedui.generated.resources.package_detail_size_value
import hikyaku.sharedui.generated.resources.package_detail_status_unknown
import hikyaku.sharedui.generated.resources.package_detail_title
import hikyaku.sharedui.generated.resources.package_detail_to
import hikyaku.sharedui.generated.resources.package_detail_volume_value
import hikyaku.sharedui.generated.resources.package_detail_weight_value
import hikyaku.sharedui.generated.resources.tracking_share_text
import io.github.jan.supabase.storage.StorageItem
import org.hikyaku.mobile.packages.model.PackageDeliveryWindow
import org.hikyaku.mobile.packages.model.PackageDetail
import org.hikyaku.mobile.packages.model.PackageDimensions
import org.hikyaku.mobile.packages.model.PackageParty
import org.hikyaku.mobile.packages.model.PackageTimelineEntry
import org.hikyaku.mobile.share.rememberShareText
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.util.formatIsoAsDisplayDate
import org.jetbrains.compose.resources.stringResource
import qrgenerator.qrkitpainter.QrKitErrorCorrection
import qrgenerator.qrkitpainter.QrKitLogo
import qrgenerator.qrkitpainter.QrKitLogoPadding
import qrgenerator.qrkitpainter.rememberQrKitPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDetailScreen(
    state: PackageDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDeletePackage: () -> Unit = {},
    onDismissDeleteError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shareText = rememberShareText()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.package_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    val trackingUrl = state.trackingUrl
                    if (trackingUrl != null) {
                        val message = stringResource(Res.string.tracking_share_text, state.orgName, trackingUrl)
                        IconButton(onClick = { shareText(message) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.action_share))
                        }
                    }
                    if (state.canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !state.isDeleting) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.detail == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.detail == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                }
            }

            state.detail != null -> {
                val detail = state.detail
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { HeroCard(detail, state.orgLogoUrl) }
                    item {
                        SectionHeader(stringResource(Res.string.package_detail_section_journey))
                        JourneyCard(detail.sender, detail.receiver)
                    }
                    item {
                        SectionHeader(stringResource(Res.string.package_detail_section_dimensions))
                        DimensionsCard(detail.dimensions)
                    }
                    val deliveryWindow = detail.deliveryWindow
                    if (deliveryWindow != null) {
                        item {
                            SectionHeader(stringResource(Res.string.package_detail_section_schedule))
                            ScheduleCard(deliveryWindow)
                        }
                    }
                    if (detail.warehouseName != null || detail.warehouseAddress != null) {
                        item {
                            SectionHeader(stringResource(Res.string.package_detail_section_origin))
                            OriginCard(detail.warehouseName, detail.warehouseAddress)
                        }
                    }
                    val deliveryNotes = detail.deliveryNotes
                    if (deliveryNotes != null) {
                        item {
                            SectionHeader(stringResource(Res.string.package_detail_section_notes))
                            NotesCard(deliveryNotes)
                        }
                    }
                    item {
                        SectionHeader(stringResource(Res.string.package_detail_section_timeline))
                        TimelineCard(detail.timeline)
                    }
                    if (state.images.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(Res.string.package_detail_section_pod))
                            PodRow(state.images)
                        }
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(Res.string.package_detail_delete_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.package_detail_delete_confirm_message,
                            state.detail?.trackingNumber.orEmpty(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDeleteConfirm = false; onDeletePackage() }) {
                        Text(stringResource(Res.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
        val deleteError = state.deleteError
        if (deleteError != null) {
            AlertDialog(
                onDismissRequest = onDismissDeleteError,
                title = { Text(stringResource(Res.string.package_detail_delete_error)) },
                text = { Text(deleteError) },
                confirmButton = {
                    TextButton(onClick = onDismissDeleteError) { Text(stringResource(Res.string.action_ok)) }
                },
            )
        }
    }
}

@Preview
@Composable
private fun PackageDetailScreenPreview() {
    HikyakuTheme {
        PackageDetailScreen(
            state = PackageDetailUiState(
                isLoading = false,
                detail = PackageDetail(
                    id = "1",
                    trackingNumber = "TRK-2024-0001",
                    createdAt = "2024-01-15T10:30:00",
                    currentStatus = "In Transit",
                    currentStatusEnum = "IN_TRANSIT",
                    deliveryNotes = "Leave at front desk if no answer.",
                    sender = PackageParty(
                        name = "Aiko Tanaka",
                        phone = "+81 90-1234-5678",
                        address = "1-2-3 Shibuya, Tokyo",
                    ),
                    receiver = PackageParty(
                        name = "Kenji Sato",
                        phone = "+81 80-9876-5432",
                        address = "4-5-6 Namba, Osaka",
                    ),
                    warehouseName = "Tokyo Central Warehouse",
                    warehouseAddress = "7-8-9 Chiyoda, Tokyo",
                    dimensions = PackageDimensions(weightKg = 4.5, lengthCm = 30.0, widthCm = 20.0, heightCm = 15.0),
                    deliveryWindow = PackageDeliveryWindow(
                        scheduledDeparture = "2024-01-15T09:00:00",
                        actualDeparture = "2024-01-15T09:15:00",
                        scheduledArrival = "2024-01-17T17:00:00",
                        actualArrival = null,
                    ),
                    timeline = listOf(
                        PackageTimelineEntry(status = "In Transit", statusEnum = "IN_TRANSIT", createdAt = "2024-01-16T08:00:00"),
                        PackageTimelineEntry(status = "Assigned", statusEnum = "ASSIGNED", createdAt = "2024-01-15T09:15:00"),
                        PackageTimelineEntry(status = "Pending", statusEnum = "PENDING", createdAt = "2024-01-15T08:00:00"),
                    ),
                ),
                images = emptyList(),
                error = null,
                orgName = "Acme Logistics",
                trackingUrl = "https://track.example.com/TRK-2024-0001",
            ),
            onBack = {},
            onRetry = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Hero: status, tracking number, QR
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard(detail: PackageDetail, orgLogoUrl: String?) {
    val printShippingLabel = rememberPrintShippingLabel()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusChip(detail.currentStatusEnum, detail.currentStatus)
            Text(
                text = detail.trackingNumber,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.package_detail_created, detail.createdDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 0.dp) {
                Image(
                    painter = rememberBrandedQrPainter(data = detail.id, logoUrl = orgLogoUrl),
                    contentDescription = stringResource(Res.string.cd_package_qr_code),
                    modifier = Modifier.size(200.dp).padding(16.dp),
                )
            }
            OutlinedButton(onClick = { printShippingLabel(detail) }) {
                Icon(
                    imageVector = PrintIcon,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.package_detail_print_label))
            }
        }
    }
}

/** Fraction of the QR code's width the logo takes up, and the cleared margin drawn around it. */
private const val QR_LOGO_SIZE = 0.25f
private const val QR_LOGO_PADDING = 0.1f

/** Pixel size the logo is decoded at - generous for the ~50dp square it ends up in. */
private const val QR_LOGO_REQUEST_PX = 256

/**
 * A QR code for [data], with [logoUrl] drawn in the middle when the org has branding to show.
 *
 * The logo only reaches the code once it has actually loaded: [rememberQrKitPainter] buffers what
 * it draws, so a painter that fills in later would never appear - passing the loaded flag as a key
 * rebuilds the code at that point instead. A logo that fails to load leaves a plain code rather
 * than a hole in the middle of one. Error correction goes to [QrKitErrorCorrection.High] so a
 * scanner can still recover the modules the logo covers.
 */
@Composable
private fun rememberBrandedQrPainter(data: String, logoUrl: String?): Painter {
    if (logoUrl == null) return rememberQrKitPainter(data = data)

    val context = LocalPlatformContext.current
    val request = remember(context, logoUrl) {
        ImageRequest.Builder(context).data(logoUrl).size(QR_LOGO_REQUEST_PX).build()
    }
    val logoPainter = rememberAsyncImagePainter(model = request)
    val logoState by logoPainter.state.collectAsState()
    val logoLoaded = logoState is AsyncImagePainter.State.Success

    return rememberQrKitPainter(data, logoLoaded) {
        if (logoLoaded) {
            errorCorrection = QrKitErrorCorrection.High
            logo = QrKitLogo(
                painter = logoPainter,
                size = QR_LOGO_SIZE,
                padding = QrKitLogoPadding.Natural(QR_LOGO_PADDING),
            )
        }
    }
}

@Composable
private fun StatusChip(statusEnum: String?, label: String?) {
    val (container, content) = statusColors(statusEnum)
    Surface(color = container, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(content, CircleShape))
            Text(
                text = label ?: stringResource(Res.string.package_detail_status_unknown),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Journey: sender -> receiver
// ---------------------------------------------------------------------------

@Composable
private fun JourneyCard(sender: PackageParty, receiver: PackageParty) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PartyRow(
                label = stringResource(Res.string.package_detail_from),
                party = sender,
                dotColor = MaterialTheme.colorScheme.primary,
            )
            // Connector aligned under the leading dot.
            Row {
                Spacer(Modifier.width(11.dp))
                Box(Modifier.width(2.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            PartyRow(
                label = stringResource(Res.string.package_detail_to),
                party = receiver,
                dotColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PartyRow(label: String, party: PackageParty, dotColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 4.dp).size(24.dp).background(dotColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = party.name ?: stringResource(Res.string.package_detail_no_name),
                style = MaterialTheme.typography.titleMedium,
            )
            party.phone?.let { phone ->
                IconLine(Icons.Filled.Phone, phone)
            }
            IconLine(Icons.Filled.Place, party.address ?: stringResource(Res.string.package_detail_no_address))
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Dimensions
// ---------------------------------------------------------------------------

@Composable
private fun DimensionsCard(dimensions: PackageDimensions?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        if (dimensions == null) {
            Text(
                text = stringResource(Res.string.package_detail_dimensions_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@ElevatedCard
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                value = stringResource(Res.string.package_detail_weight_value, formatDecimal(dimensions.weightKg)),
                label = stringResource(Res.string.package_detail_label_weight),
            )
            StatTile(
                modifier = Modifier.weight(1f),
                value = stringResource(
                    Res.string.package_detail_size_value,
                    formatDecimal(dimensions.lengthCm),
                    formatDecimal(dimensions.widthCm),
                    formatDecimal(dimensions.heightCm),
                ),
                label = stringResource(Res.string.package_detail_label_size),
            )
            StatTile(
                modifier = Modifier.weight(1f),
                value = stringResource(Res.string.package_detail_volume_value, formatDecimal(dimensions.volumeCm3)),
                label = stringResource(Res.string.package_detail_label_volume),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Delivery window
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleCard(window: PackageDeliveryWindow) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScheduleRow(stringResource(Res.string.package_detail_scheduled_departure), window.scheduledDeparture)
            ScheduleRow(stringResource(Res.string.package_detail_actual_departure), window.actualDeparture)
            ScheduleRow(stringResource(Res.string.package_detail_scheduled_arrival), window.scheduledArrival)
            ScheduleRow(stringResource(Res.string.package_detail_actual_arrival), window.actualArrival)
        }
    }
}

@Composable
private fun ScheduleRow(label: String, timestamp: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = timestamp?.let(::formatTimestamp) ?: stringResource(Res.string.package_detail_pending),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (timestamp != null) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------------------
// Origin & notes
// ---------------------------------------------------------------------------

@Composable
private fun OriginCard(name: String?, address: String?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            address?.let { IconLine(Icons.Filled.Place, it) }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Status timeline
// ---------------------------------------------------------------------------

@Composable
private fun TimelineCard(entries: List<PackageTimelineEntry>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.package_detail_no_timeline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@ElevatedCard
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            entries.forEachIndexed { index, entry ->
                TimelineEntryRow(entry, isLast = index == entries.lastIndex)
            }
        }
    }
}

@Composable
private fun TimelineEntryRow(entry: PackageTimelineEntry, isLast: Boolean) {
    val (_, accent) = statusColors(entry.statusEnum)
    Row(verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 4.dp).size(12.dp).background(accent, CircleShape))
            if (!isLast) {
                Box(Modifier.width(2.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Column(Modifier.padding(start = 12.dp, bottom = if (isLast) 0.dp else 12.dp)) {
            Text(entry.status, style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatTimestamp(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Proof-of-delivery photos
// ---------------------------------------------------------------------------

@Composable
private fun PodRow(images: List<StorageItem>) {
    val description = stringResource(Res.string.cd_package_photo)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images) { item ->
            AsyncImage(
                model = item,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Container + content colour for a package status, keyed by its machine enum. */
@Composable
private fun statusColors(statusEnum: String?): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (statusEnum) {
        "DELIVERED" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "IN_TRANSIT", "ONBOARD_FOR_DELIVERY" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "ASSIGNED" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        "FAILED" -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

/** Renders an ISO timestamp for user-facing display, e.g. "24 July 2026 · 14:30". */
private fun formatTimestamp(iso: String): String {
    val date = formatIsoAsDisplayDate(iso)
    val time = if (iso.length >= 16 && iso[10] == 'T') iso.substring(11, 16) else null
    return if (time != null) "$date · $time" else date
}

/** Drops a trailing `.0` for whole numbers, e.g. dimensions entered without decimals. */
private fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
