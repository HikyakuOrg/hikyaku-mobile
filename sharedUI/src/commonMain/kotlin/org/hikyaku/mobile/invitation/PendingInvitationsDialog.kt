package org.hikyaku.mobile.invitation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.invitation_accept
import hikyaku.sharedui.generated.resources.invitation_decline
import hikyaku.sharedui.generated.resources.invitation_dialog_title
import hikyaku.sharedui.generated.resources.invitation_role_label
import org.hikyaku.mobile.auth.InvitationsUiState
import org.hikyaku.mobile.invitation.model.Invitation
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Blocking full-screen prompt shown whenever [InvitationsUiState.invitations] is non-empty,
 * matching how the web dashboard blocks entry behind an accept/decline modal until every
 * outstanding invitation is dealt with. Not user-dismissible (no close button, no outside-tap
 * dismiss) — it disappears on its own once [onAccept]/[onDecline] have resolved every row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingInvitationsDialog(
    state: InvitationsUiState,
    onAccept: (invitationId: String) -> Unit,
    onDecline: (invitationId: String) -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { TopAppBar(title = { Text(stringResource(Res.string.invitation_dialog_title)) }) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.invitations, key = { it.id }) { invitation ->
                    InvitationRow(
                        invitation = invitation,
                        isActing = state.actingOnId == invitation.id,
                        actionsEnabled = state.actingOnId == null,
                        error = state.errorByInvitationId[invitation.id],
                        onAccept = { onAccept(invitation.id) },
                        onDecline = { onDecline(invitation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationRow(
    invitation: Invitation,
    isActing: Boolean,
    actionsEnabled: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = invitation.organisationName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.invitation_role_label, invitation.role),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            if (isActing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDecline, enabled = actionsEnabled) {
                        Text(stringResource(Res.string.invitation_decline))
                    }
                    Button(onClick = onAccept, enabled = actionsEnabled) {
                        Text(stringResource(Res.string.invitation_accept))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PendingInvitationsDialogPreview() {
    HikyakuTheme {
        PendingInvitationsDialog(
            state = InvitationsUiState(
                invitations = listOf(
                    Invitation(
                        id = "inv-1",
                        organisationId = "org-1",
                        organisationSlug = "acme-logistics",
                        organisationName = "Acme Logistics",
                        role = "Driver",
                        permissions = emptyList(),
                    ),
                ),
            ),
            onAccept = {},
            onDecline = {},
        )
    }
}
