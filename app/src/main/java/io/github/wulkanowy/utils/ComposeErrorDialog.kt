package io.github.wulkanowy.utils

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wulkanowy.R
import javax.inject.Inject


@Composable
fun ErrorDialog(error: Throwable, onDismiss: () -> Unit) {
    val errorState = rememberSaveable(error) { error }
    var confirmBugReportDialog by remember { mutableStateOf(false) }

    if (confirmBugReportDialog) {
        ConfirmDialog(errorState, onDismiss)
    } else {
        InfoDialog(errorState, onDismiss) { confirmBugReportDialog = true }
    }
}

@Composable
private fun InfoDialog(error: Throwable, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val title = error.resString()
    val message = error.localizedMessage.takeIf { !it.isNullOrBlank() }
    val content = error.stackTraceToString().replace(": ${error.localizedMessage}", "")

    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        SelectionContainer {
            Column {
                message?.let {
                    Text(it)
                }
                Text(
                    content,
                    Modifier
                        .heightIn(max = 200.dp)
                        .weight(1f, fill = false)
                    // FIXME Fix
                    //    .verticalScroll(rememberScrollState())
                )
            }
        }
    }, buttons = {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            TextButton(
                onClick = onConfirm, enabled = error.isShouldBeReported()
            ) {
                Text(text = stringResource(id = R.string.about_feedback))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
                TextButton(onClick = {
                    val stacktrace = AnnotatedString(error.stackTraceToString())
                    clipboardManager.setText(stacktrace)
                    Toast.makeText(context, R.string.all_copied, Toast.LENGTH_LONG).show()
                }) {
                    Text(text = stringResource(id = android.R.string.copy))
                }
            }
        }
    })
}

@HiltViewModel
private class ConfirmDialogViewModel @Inject constructor(val appInfo: AppInfo) : ViewModel()

@Composable
private fun ConfirmDialog(error: Throwable, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ConfirmDialogViewModel = viewModel()
    val appInfo = viewModel.appInfo

    val emailTitle = stringResource(R.string.about_feedback)
    val emailBody = stringResource(
        R.string.about_feedback_template,
        "${appInfo.systemManufacturer} ${appInfo.systemModel}",
        appInfo.systemVersion.toString(),
        "${appInfo.versionName}-${appInfo.buildFlavor}"
    ) + "\n" + error.stackTraceToString()

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_error_check_update)) },
        text = { Text(stringResource(id = R.string.dialog_error_check_update_message)) },
        buttons = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                TextButton(onClick = {
                    context.openEmailClient(chooserTitle = emailTitle,
                        email = "wulkanowyinc@gmail.com",
                        subject = "Zgłoszenie błędu",
                        body = emailBody,
                        onActivityNotFound = {
                            context.openInternetBrowser("https://github.com/wulkanowy/wulkanowy/issues") {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        })
                }, content = {
                    Text(stringResource(R.string.about_feedback))
                })
                TextButton(onClick = {
                    context.openAppInMarket {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                }, content = {
                    Text(stringResource(id = R.string.dialog_error_check_update))
                })
            }
        })
}
