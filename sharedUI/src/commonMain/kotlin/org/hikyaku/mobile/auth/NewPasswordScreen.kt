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
import hikyaku.sharedui.generated.resources.auth_hide_password
import hikyaku.sharedui.generated.resources.auth_label_confirm_password
import hikyaku.sharedui.generated.resources.auth_label_password
import hikyaku.sharedui.generated.resources.auth_new_password_submit
import hikyaku.sharedui.generated.resources.auth_new_password_title
import hikyaku.sharedui.generated.resources.auth_show_password
import org.jetbrains.compose.resources.stringResource

/**
 * Shown after a password-reset code has been verified. There's no back/cancel action: the user
 * already holds a valid (recovery) session at this point, so the only way forward is to set a
 * new password.
 */
@Composable
fun NewPasswordScreen(
    state: AuthScreenState,
    onSubmit: (newPassword: String, confirmPassword: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.auth_new_password_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(Res.string.auth_label_confirm_password)) },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) VisibilityOffIcon else VisibilityIcon,
                        contentDescription = stringResource(
                            if (confirmPasswordVisible) Res.string.auth_hide_password else Res.string.auth_show_password,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(password, confirmPassword) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.auth_new_password_submit)) }
        MessageArea(state)
    }
}

@Preview
@Composable
private fun NewPasswordScreenPreview() {
    HikyakuTheme {
        NewPasswordScreen(
            state = AuthScreenState(),
            onSubmit = { _, _ -> },
        )
    }
}
