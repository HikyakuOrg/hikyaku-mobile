package org.hikyaku.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.account_default_badge
import hikyaku.sharedui.generated.resources.account_edit_name
import hikyaku.sharedui.generated.resources.account_edit_name_save
import hikyaku.sharedui.generated.resources.account_edit_name_title
import hikyaku.sharedui.generated.resources.account_no_display_name
import hikyaku.sharedui.generated.resources.account_photo_option_camera
import hikyaku.sharedui.generated.resources.account_photo_option_gallery
import hikyaku.sharedui.generated.resources.account_sign_out
import hikyaku.sharedui.generated.resources.account_switch_organisation
import hikyaku.sharedui.generated.resources.action_cancel
import hikyaku.sharedui.generated.resources.auth_label_display_name
import hikyaku.sharedui.generated.resources.cd_profile_picture
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.organisation.model.Organisation
import org.hikyaku.mobile.shift.rememberImagePicker
import org.hikyaku.mobile.shift.rememberPhotoCapture
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Circular profile picture. Shows the uploaded photo from [avatarUrl] when available,
 * falling back to a coloured circle with the user's initial (Gmail-style) while the image
 * is loading, fails, or is unset.
 */
@Composable
fun ProfileAvatar(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // The initial always renders underneath, so it shows through if the image is
        // missing or fails to load.
        Text(
            text = initialOf(displayName, email),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = stringResource(Res.string.cd_profile_picture),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

@Preview
@Composable
private fun ProfileAvatarPreview() {
    HikyakuTheme {
        ProfileAvatar(
            displayName = "Jane Doe",
            email = "jane.doe@example.com",
            avatarUrl = null,
            size = 48.dp,
        )
    }
}

/**
 * Tapping [ProfileAvatar] opens a menu offering to take a new photo or pick one from the
 * gallery; while [isUploading] a spinner dims the avatar and taps are ignored.
 */
@Composable
private fun EditableProfileAvatar(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    size: Dp,
    isUploading: Boolean,
    onCapturePhoto: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        ProfileAvatar(
            displayName = displayName,
            email = email,
            avatarUrl = avatarUrl,
            size = size,
            modifier = Modifier.clickable(enabled = !isUploading) { expanded = true },
        )
        if (isUploading) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size / 2),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.account_photo_option_camera)) },
                onClick = { expanded = false; onCapturePhoto() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.account_photo_option_gallery)) },
                onClick = { expanded = false; onPickImage() },
            )
        }
    }
}

private fun initialOf(displayName: String?, email: String?): String {
    val source = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: email?.trim()?.takeIf { it.isNotEmpty() }
    return source?.first()?.uppercase() ?: "?"
}

/**
 * Gmail-style account sheet: a profile header, the list of organisations the user can switch
 * between (current one marked), and a sign-out action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    user: AuthState.Authenticated,
    organisations: List<Organisation>,
    selectedOrgId: String?,
    onSelectOrg: (String) -> Unit,
    onSaveDisplayName: (name: String, onResult: (String?) -> Unit) -> Unit,
    onUploadAvatar: (bytes: ByteArray, onResult: (String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showEditName by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    fun uploadAvatar(bytes: ByteArray) {
        isUploadingAvatar = true
        avatarError = null
        onUploadAvatar(bytes) { error ->
            isUploadingAvatar = false
            avatarError = error
        }
    }
    val capturePhoto = rememberPhotoCapture { bytes -> if (bytes != null) uploadAvatar(bytes) }
    val pickImage = rememberImagePicker { images -> images.firstOrNull()?.let(::uploadAvatar) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Profile header.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditableProfileAvatar(
                    displayName = user.displayName,
                    email = user.email,
                    avatarUrl = user.avatarUrl,
                    size = 48.dp,
                    isUploading = isUploadingAvatar,
                    onCapturePhoto = capturePhoto,
                    onPickImage = pickImage,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = user.displayName ?: stringResource(Res.string.account_no_display_name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    user.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = { showEditName = true }) {
                    Text(stringResource(Res.string.account_edit_name))
                }
            }
            avatarError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(Res.string.account_switch_organisation),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            organisations.forEach { org ->
                OrgRow(
                    organisation = org,
                    selected = org.id == selectedOrgId,
                    onClick = {
                        onSelectOrg(org.id)
                        onDismiss()
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TextButton(
                onClick = {
                    onSignOut()
                    onDismiss()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text(stringResource(Res.string.account_sign_out)) }
        }
    }

    if (showEditName) {
        EditDisplayNameDialog(
            currentName = user.displayName.orEmpty(),
            onSave = onSaveDisplayName,
            onDismiss = { showEditName = false },
        )
    }
}

@Preview
@Composable
private fun AccountSwitcherSheetPreview() {
    HikyakuTheme {
        AccountSwitcherSheet(
            user = AuthState.Authenticated(
                userId = "user-1",
                email = "jane.doe@example.com",
                displayName = "Jane Doe",
                avatarUrl = null,
            ),
            organisations = listOf(
                Organisation(id = "org-1", name = "Personal", slug = "jane-doe", orgType = "personal", createdBy = "user-1"),
                Organisation(id = "org-2", name = "Acme Logistics", slug = "acme-logistics", orgType = "team", createdBy = "user-1"),
            ),
            selectedOrgId = "org-1",
            onSelectOrg = {},
            onSaveDisplayName = { _, _ -> },
            onUploadAvatar = { _, _ -> },
            onSignOut = {},
            onDismiss = {},
        )
    }
}

/**
 * Dialog for editing the display name. Keeps its own text/saving/error state and only
 * dismisses once the save succeeds; failures surface inline so the user can retry.
 */
@Composable
private fun EditDisplayNameDialog(
    currentName: String,
    onSave: (name: String, onResult: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(Res.string.account_edit_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text(stringResource(Res.string.auth_label_display_name)) },
                singleLine = true,
                isError = error != null,
                enabled = !isSaving,
                supportingText = error?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    onSave(name) { result ->
                        isSaving = false
                        if (result == null) onDismiss() else error = result
                    }
                },
            ) { Text(stringResource(Res.string.account_edit_name_save)) }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun OrgRow(
    organisation: Organisation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(organisation.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = organisation.orgType.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (organisation.isPersonal) {
            DefaultBadge()
            Spacer(Modifier.width(8.dp))
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun DefaultBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = stringResource(Res.string.account_default_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
