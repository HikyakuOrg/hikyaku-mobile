package org.hikyaku.mobile.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.hikyaku.mobile.theme.HikyakuTheme
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_back
import hikyaku.sharedui.generated.resources.auth_forgot_password
import hikyaku.sharedui.generated.resources.auth_label_email
import hikyaku.sharedui.generated.resources.auth_label_password
import hikyaku.sharedui.generated.resources.auth_hide_password
import hikyaku.sharedui.generated.resources.auth_show_password
import hikyaku.sharedui.generated.resources.auth_sign_in
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignInScreen(
    state: AuthScreenState,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.auth_sign_in), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(Res.string.auth_label_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.auth_label_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) VisibilityOffIcon else VisibilityIcon,
                        contentDescription = stringResource(
                            if (passwordVisible) Res.string.auth_hide_password else Res.string.auth_show_password,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onForgotPassword(email) }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.auth_forgot_password))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSignIn(email, password) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.auth_sign_in)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
        MessageArea(state)
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    HikyakuTheme {
        SignInScreen(
            state = AuthScreenState(),
            onSignIn = { _, _ -> },
            onForgotPassword = {},
            onBack = {},
        )
    }
}
