package org.hikyaku.mobile.packages.optimisation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.cd_package_optimise_close
import hikyaku.sharedui.generated.resources.package_optimise_failed_title
import hikyaku.sharedui.generated.resources.package_optimise_running_count
import hikyaku.sharedui.generated.resources.package_optimise_succeeded_count
import hikyaku.sharedui.generated.resources.package_optimise_succeeded_title
import hikyaku.sharedui.generated.resources.package_optimise_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen dialog shown for [progress]'s whole lifecycle: queuing the run, polling it while
 * [OptimisationProgress.Phase.RUNNING], then its terminal outcome. Mirrors the `Dialog` +
 * `Scaffold` shell [org.hikyaku.mobile.shift.scan.ScanPackagesOverlay] uses for the same
 * full-screen-overlay-over-an-existing-screen idiom. [onClose] cancels the run's polling (the run
 * itself keeps going server-side) while [OptimisationProgress.Phase.RUNNING], and simply dismisses
 * once it has reached a terminal phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimisationProgressDialog(progress: OptimisationProgress, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.package_optimise_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_package_optimise_close))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (progress.phase) {
                        OptimisationProgress.Phase.RUNNING -> {
                            CircularProgressIndicator(modifier = Modifier.size(96.dp))
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.package_optimise_running_count,
                                    progress.packageCount,
                                    progress.packageCount,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }

                        OptimisationProgress.Phase.SUCCEEDED -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(Res.string.package_optimise_succeeded_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.package_optimise_succeeded_count,
                                    progress.packageCount,
                                    progress.packageCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_ok)) }
                        }

                        OptimisationProgress.Phase.FAILED -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(Res.string.package_optimise_failed_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = progress.message.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            TextButton(onClick = onClose) { Text(stringResource(Res.string.action_ok)) }
                        }
                    }
                }
            }
        }
    }
}
