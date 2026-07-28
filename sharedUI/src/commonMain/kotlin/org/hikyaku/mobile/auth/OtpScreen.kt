package org.hikyaku.mobile.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.hikyaku.mobile.theme.HikyakuTheme
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.auth_label_otp_code
import hikyaku.sharedui.generated.resources.auth_otp_resend
import hikyaku.sharedui.generated.resources.auth_otp_verify
import org.jetbrains.compose.resources.stringResource

@Composable
fun OtpScreen(
    title: String,
    description: String,
    state: AuthScreenState,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) code = new },
            label = { Text(stringResource(Res.string.auth_label_otp_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onVerify(code) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.auth_otp_verify)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onResend, enabled = !state.isLoading) {
            Text(stringResource(Res.string.auth_otp_resend))
        }
        TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
        MessageArea(state)
    }
}

@Preview
@Composable
private fun OtpScreenPreview() {
    HikyakuTheme {
        OtpScreen(
            title = "Verify your email",
            description = "Enter the 6-digit code sent to you@example.com",
            state = AuthScreenState(),
            onVerify = {},
            onResend = {},
            onBack = {},
        )
    }
}
