package org.hikyaku.mobile.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.auth_otp_description
import hikyaku.sharedui.generated.resources.auth_otp_reset_description
import hikyaku.sharedui.generated.resources.auth_otp_reset_title
import hikyaku.sharedui.generated.resources.auth_otp_title
import org.hikyaku.mobile.environment.EnvironmentScreen
import org.hikyaku.mobile.environment.EnvironmentViewModel
import org.hikyaku.mobile.theme.HikyakuTheme
import org.hikyaku.mobile.toast.ToastEffect
import org.jetbrains.compose.resources.stringResource

object AuthRoutes {
    const val WELCOME = "welcome"
    const val SIGN_IN = "signIn"
    const val SIGN_UP = "signUp"
    const val OTP_VERIFY = "otpVerify"
    const val PASSWORD_RESET_OTP = "passwordResetOtp"
    const val ENVIRONMENT = "environment"
}

@Composable
fun AuthNavGraph(
    viewModel: AuthViewModel,
    environment: EnvironmentViewModel.UiState,
    onConnectSelfHosted: (String) -> Unit,
    onClearEnvError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val state by viewModel.screenState.collectAsState()
    val pendingVerificationEmail by viewModel.pendingVerificationEmail.collectAsState()
    val pendingPasswordResetEmail by viewModel.pendingPasswordResetEmail.collectAsState()

    LaunchedEffect(pendingVerificationEmail) {
        if (pendingVerificationEmail != null) {
            navController.navigate(AuthRoutes.OTP_VERIFY)
        }
    }
    LaunchedEffect(pendingPasswordResetEmail) {
        if (pendingPasswordResetEmail != null) {
            navController.navigate(AuthRoutes.PASSWORD_RESET_OTP)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.WELCOME,
        modifier = modifier,
    ) {
        composable(AuthRoutes.WELCOME) {
            WelcomeScreen(
                onSignInClick = { viewModel.clearMessages(); navController.navigate(AuthRoutes.SIGN_IN) },
                onSignUpClick = { viewModel.clearMessages(); navController.navigate(AuthRoutes.SIGN_UP) },
                onSelfHostedClick = { onClearEnvError(); navController.navigate(AuthRoutes.ENVIRONMENT) },
            )
        }
        composable(AuthRoutes.SIGN_IN) {
            SignInScreen(
                state = state,
                onSignIn = viewModel::signIn,
                onForgotPassword = viewModel::sendPasswordReset,
                onBack = { viewModel.clearMessages(); navController.popBackStack() },
            )
        }
        composable(AuthRoutes.SIGN_UP) {
            SignUpScreen(
                state = state,
                onSignUp = viewModel::signUp,
                onBack = { viewModel.clearMessages(); navController.popBackStack() },
            )
        }
        composable(AuthRoutes.OTP_VERIFY) {
            OtpScreen(
                title = stringResource(Res.string.auth_otp_title),
                description = stringResource(Res.string.auth_otp_description, pendingVerificationEmail.orEmpty()),
                state = state,
                onVerify = viewModel::verifyOtp,
                onResend = viewModel::resendOtp,
                onBack = {
                    viewModel.clearPendingVerification()
                    viewModel.clearMessages()
                    navController.popBackStack()
                },
            )
        }
        composable(AuthRoutes.PASSWORD_RESET_OTP) {
            OtpScreen(
                title = stringResource(Res.string.auth_otp_reset_title),
                description = stringResource(Res.string.auth_otp_reset_description, pendingPasswordResetEmail.orEmpty()),
                state = state,
                onVerify = viewModel::verifyPasswordResetOtp,
                onResend = viewModel::resendPasswordResetOtp,
                onBack = {
                    viewModel.clearPendingPasswordReset()
                    viewModel.clearMessages()
                    navController.popBackStack()
                },
            )
        }
        composable(AuthRoutes.ENVIRONMENT) {
            EnvironmentScreen(
                state = environment,
                onConnectSelfHosted = onConnectSelfHosted,
                onClearError = onClearEnvError,
                onBack = { onClearEnvError(); navController.popBackStack() },
            )
        }
    }
}

/** Shared loading / error / info footer used by the auth screens. */
@Composable
fun MessageArea(state: AuthScreenState, modifier: Modifier = Modifier) {
    ToastEffect(state.errorMessage)
    ToastEffect(state.infoMessage)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (state.isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

@Preview
@Composable
private fun MessageAreaPreview() {
    HikyakuTheme {
        MessageArea(state = AuthScreenState(isLoading = true))
    }
}
