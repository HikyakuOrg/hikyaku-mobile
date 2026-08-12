package org.hikyaku.mobile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.account_error_update_name_failed
import hikyaku.sharedui.generated.resources.account_error_update_photo_failed
import hikyaku.sharedui.generated.resources.auth_error_google_sign_in_failed
import hikyaku.sharedui.generated.resources.auth_error_missing_credentials
import hikyaku.sharedui.generated.resources.auth_error_missing_display_name
import hikyaku.sharedui.generated.resources.auth_error_missing_email
import hikyaku.sharedui.generated.resources.auth_error_missing_email_for_reset
import hikyaku.sharedui.generated.resources.auth_error_password_reset_failed
import hikyaku.sharedui.generated.resources.auth_error_password_too_short
import hikyaku.sharedui.generated.resources.auth_error_password_update_failed
import hikyaku.sharedui.generated.resources.auth_error_passwords_mismatch
import hikyaku.sharedui.generated.resources.auth_error_sign_in_failed
import hikyaku.sharedui.generated.resources.auth_error_invalid_otp
import hikyaku.sharedui.generated.resources.auth_error_otp_failed
import hikyaku.sharedui.generated.resources.auth_error_otp_resend_failed
import hikyaku.sharedui.generated.resources.auth_error_sign_up_failed
import hikyaku.sharedui.generated.resources.auth_info_otp_resent
import hikyaku.sharedui.generated.resources.auth_info_password_reset_sent
import hikyaku.sharedui.generated.resources.error_load_organisations
import hikyaku.sharedui.generated.resources.error_load_shifts
import hikyaku.sharedui.generated.resources.home_delete_blocked_message
import hikyaku.sharedui.generated.resources.shift_error_delete_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.auth.model.SignUpOutcome
import org.hikyaku.mobile.organisation.OrganisationRepository
import org.hikyaku.mobile.organisation.model.Organisation
import org.hikyaku.mobile.organisation.OrganisationStore
import org.hikyaku.mobile.shift.ShiftRepository
import org.hikyaku.mobile.shift.model.Shift
import org.hikyaku.mobile.shift.session.ShiftSessionStore
import org.hikyaku.mobile.shift.session.model.ShiftSession
import org.jetbrains.compose.resources.getString

data class AuthScreenState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

data class HomeUiState(
    val isLoadingOrgs: Boolean = false,
    val organisations: List<Organisation> = emptyList(),
    val orgError: String? = null,
    /** The organisation currently selected on the home screen. */
    val selectedOrgId: String? = null,
) {
    val selectedOrganisation: Organisation?
        get() = organisations.firstOrNull { it.id == selectedOrgId }
}

data class ShiftsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val shifts: List<Shift> = emptyList(),
    val error: String? = null,
    /** The organisation the [shifts] belong to, used to ignore stale responses. */
    val orgId: String? = null,
    /** Ids of shifts whose every package has been delivered; shown under "Completed". */
    val completedShiftIds: Set<String> = emptySet(),
    /** Total packages to deliver per shift id, keyed by `vrp_optimization` id. */
    val packageCounts: Map<String, Int> = emptyMap(),
    /** Ids of shifts with at least one delivered package; these can no longer be deleted. */
    val nonDeletableShiftIds: Set<String> = emptySet(),
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
    private val organisationRepository: OrganisationRepository = OrganisationRepository(),
    private val shiftRepository: ShiftRepository = ShiftRepository(),
    private val organisationStore: OrganisationStore = OrganisationStore(),
    private val sessionStore: ShiftSessionStore = ShiftSessionStore(),
) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    /**
     * Set to the persisted active shift when the user is authenticated, so the app can jump
     * straight back into a shift that was interrupted by a kill. Cleared via [consumeResume]
     * once navigation has happened.
     */
    private val _resumeSession = MutableStateFlow<ShiftSession?>(null)
    val resumeSession: StateFlow<ShiftSession?> = _resumeSession.asStateFlow()

    private val _screenState = MutableStateFlow(AuthScreenState())
    val screenState: StateFlow<AuthScreenState> = _screenState.asStateFlow()

    /** The email awaiting OTP confirmation after sign-up, or null when no verification is pending. */
    private val _pendingVerificationEmail = MutableStateFlow<String?>(null)
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    /** The email awaiting a password-reset code, or null when no reset is pending. */
    private val _pendingPasswordResetEmail = MutableStateFlow<String?>(null)
    val pendingPasswordResetEmail: StateFlow<String?> = _pendingPasswordResetEmail.asStateFlow()

    /**
     * True once a password-reset code has been verified (which signs the user in) but they
     * haven't yet chosen a new password. While true, the caller should show [NewPasswordScreen]
     * instead of routing into the main app, even though [authState] already reports Authenticated.
     */
    private val _isRecoveryPending = MutableStateFlow(false)
    val isRecoveryPending: StateFlow<Boolean> = _isRecoveryPending.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _shiftState = MutableStateFlow(ShiftsUiState())
    val shiftState: StateFlow<ShiftsUiState> = _shiftState.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collect { state ->
                _resumeSession.value =
                    if (state is AuthState.Authenticated) sessionStore.load()?.takeIf { it.isActive } else null
            }
        }
    }

    /** Clears the pending resume target after the app has navigated into the shift. */
    fun consumeResume() {
        _resumeSession.value = null
    }

    /**
     * Loads the organisations the signed-in user belongs to, first provisioning their personal
     * organisation if this is their first time landing here since signing up and verifying their
     * email (a no-op on every later call, once that organisation exists).
     */
    fun loadOrganisations() {
        _homeState.value = _homeState.value.copy(isLoadingOrgs = true, orgError = null)
        viewModelScope.launch {
            organisationRepository.ensurePersonalOrganisation()
            organisationRepository.fetchOrganisations()
                .onSuccess { orgs ->
                    val selectedId = initialSelection(orgs)
                    _homeState.value = HomeUiState(
                        isLoadingOrgs = false,
                        organisations = orgs,
                        selectedOrgId = selectedId,
                    )
                    selectedId?.let {
                        organisationStore.saveSelectedOrgId(it)
                        loadShifts(it)
                    }
                }
                .onFailure {
                    _homeState.value = HomeUiState(
                        isLoadingOrgs = false,
                        orgError = it.message ?: getString(Res.string.error_load_organisations),
                    )
                }
        }
    }

    /**
     * Chooses which organisation to show first: the last one the user selected (when it is
     * still in their list), otherwise their personal workspace, otherwise the first org.
     */
    private fun initialSelection(orgs: List<Organisation>): String? {
        if (orgs.isEmpty()) return null
        val saved = organisationStore.loadSelectedOrgId()
        return orgs.firstOrNull { it.id == saved }?.id
            ?: orgs.firstOrNull { it.isPersonal }?.id
            ?: orgs.first().id
    }

    /** Switches the active organisation, persists the choice, and loads its shifts. */
    fun selectOrganisation(orgId: String) {
        if (_homeState.value.selectedOrgId == orgId) return
        _homeState.value = _homeState.value.copy(selectedOrgId = orgId)
        organisationStore.saveSelectedOrgId(orgId)
        loadShifts(orgId)
    }

    /** Loads the shifts for [orgId], the organisation currently selected on the home screen. */
    fun loadShifts(orgId: String) {
        _shiftState.value = ShiftsUiState(isLoading = true, orgId = orgId)
        viewModelScope.launch { fetchShifts(orgId) }
    }

    /** Re-fetches shifts for the currently selected organisation for pull-to-refresh, keeping the visible list while it loads. */
    fun refreshShifts() {
        val s = _shiftState.value
        val orgId = s.orgId ?: return
        if (s.isLoading || s.isRefreshing) return
        _shiftState.value = s.copy(isRefreshing = true)
        viewModelScope.launch { fetchShifts(orgId) }
    }

    private suspend fun fetchShifts(orgId: String) {
        shiftRepository.fetchShifts(orgId)
            .onSuccess { shifts ->
                // Ignore a response that arrives after the user switched orgs.
                if (_shiftState.value.orgId != orgId) return@onSuccess
                val progress = shiftRepository.fetchShiftProgress(orgId).getOrNull().orEmpty()
                if (_shiftState.value.orgId != orgId) return@onSuccess
                _shiftState.value = ShiftsUiState(
                    shifts = shifts,
                    orgId = orgId,
                    completedShiftIds = progress.filterValues { it.isComplete }.keys,
                    packageCounts = progress.mapValues { (_, p) -> p.total },
                    nonDeletableShiftIds = progress.filterValues { it.hasDelivered }.keys,
                )
            }
            .onFailure {
                if (_shiftState.value.orgId != orgId) return@onFailure
                _shiftState.value = _shiftState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = it.message ?: getString(Res.string.error_load_shifts),
                )
            }
    }

    /**
     * Deletes [shiftId] from [orgId]. Removes it from [shiftState] optimistically; if the delete
     * fails, the shift is put back and [onResult] receives an error message to show. Refuses to
     * delete a shift with any delivered package — the UI should already be blocking this via
     * [ShiftsUiState.nonDeletableShiftIds], but this guard covers any other caller.
     */
    fun deleteShift(orgId: String, shiftId: String, onResult: (String?) -> Unit) {
        if (shiftId in _shiftState.value.nonDeletableShiftIds) {
            viewModelScope.launch { onResult(getString(Res.string.home_delete_blocked_message)) }
            return
        }
        val removed = _shiftState.value.shifts.firstOrNull { it.id == shiftId }
        _shiftState.value = _shiftState.value.copy(shifts = _shiftState.value.shifts.filterNot { it.id == shiftId })
        viewModelScope.launch {
            shiftRepository.deleteShift(orgId, shiftId)
                .onSuccess { onResult(null) }
                .onFailure {
                    if (removed != null && _shiftState.value.orgId == orgId) {
                        _shiftState.value = _shiftState.value.copy(
                            shifts = (_shiftState.value.shifts + removed).sortedByDescending { it.createdAt },
                        )
                    }
                    onResult(it.message ?: getString(Res.string.shift_error_delete_failed))
                }
        }
    }

    fun clearMessages() {
        _screenState.value = _screenState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun signIn(email: String, password: String) {
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            viewModelScope.launch { setError(getString(Res.string.auth_error_missing_credentials)) }
            return
        }
        launchAuth {
            repository.signIn(e, password)
                .onFailure { setError(getString(Res.string.auth_error_sign_in_failed)) }
        }
    }

    /**
     * Completes Google sign-in/sign-up from the [GoogleIdToken] obtained via
     * [rememberGoogleIdTokenLauncher]. A failed [result] (including the user cancelling the
     * account picker) is swallowed silently rather than shown as an error.
     */
    fun signInWithGoogle(result: Result<GoogleIdToken>) {
        val token = result.getOrNull() ?: return
        launchAuth {
            repository.signInWithGoogle(token.idToken, token.rawNonce)
                .onFailure { setError(getString(Res.string.auth_error_google_sign_in_failed)) }
        }
    }

    fun signUp(displayName: String, email: String, password: String, confirmPassword: String) {
        val name = displayName.trim()
        val e = email.trim()
        viewModelScope.launch {
            val problem = when {
                name.isBlank() -> getString(Res.string.auth_error_missing_display_name)
                e.isBlank() -> getString(Res.string.auth_error_missing_email)
                password.length < 6 -> getString(Res.string.auth_error_password_too_short)
                password != confirmPassword -> getString(Res.string.auth_error_passwords_mismatch)
                else -> null
            }
            if (problem != null) return@launch setError(problem)
            launchAuth {
                repository.signUp(name, e, password)
                    .onSuccess { outcome ->
                        if (outcome == SignUpOutcome.NeedsEmailConfirmation) {
                            _pendingVerificationEmail.value = e
                        }
                    }
                    .onFailure { setError(it.message ?: getString(Res.string.auth_error_sign_up_failed)) }
            }
        }
    }

    /** Clears the pending sign-up verification, e.g. when the user backs out of the OTP screen. */
    fun clearPendingVerification() {
        _pendingVerificationEmail.value = null
    }

    /** Verifies the 6-digit code sent to the pending sign-up email, completing the sign-up. */
    fun verifyOtp(token: String) {
        val email = _pendingVerificationEmail.value ?: return
        val code = token.trim()
        viewModelScope.launch {
            val problem = when {
                code.length != 6 || code.any { !it.isDigit() } -> getString(Res.string.auth_error_invalid_otp)
                else -> null
            }
            if (problem != null) return@launch setError(problem)
            launchAuth {
                repository.verifySignUpOtp(email, code)
                    .onSuccess { clearPendingVerification() }
                    .onFailure { setError(it.message ?: getString(Res.string.auth_error_otp_failed)) }
            }
        }
    }

    /** Requests a new OTP code for the pending sign-up email. */
    fun resendOtp() {
        val email = _pendingVerificationEmail.value ?: return
        launchAuth {
            repository.resendSignUpOtp(email)
                .onSuccess { setInfo(getString(Res.string.auth_info_otp_resent)) }
                .onFailure { setError(it.message ?: getString(Res.string.auth_error_otp_resend_failed)) }
        }
    }

    /**
     * Updates the signed-in user's display name. [onResult] is invoked with `null` on success,
     * or an error message to show. The authenticated state refreshes automatically once the
     * update lands, so callers don't need to reload it.
     */
    fun updateDisplayName(displayName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val name = displayName.trim()
            if (name.isBlank()) {
                onResult(getString(Res.string.auth_error_missing_display_name))
                return@launch
            }
            _screenState.value = AuthScreenState(isLoading = true)
            val result = repository.updateDisplayName(name)
            _screenState.value = _screenState.value.copy(isLoading = false)
            result
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: getString(Res.string.account_error_update_name_failed)) }
        }
    }

    /**
     * Uploads [bytes] as the signed-in user's profile picture. [onResult] is invoked with `null`
     * on success, or an error message to show. The authenticated state refreshes automatically
     * once the update lands, so callers don't need to reload it.
     */
    fun uploadAvatar(bytes: ByteArray, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.uploadAvatar(bytes)
            result
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message ?: getString(Res.string.account_error_update_photo_failed)) }
        }
    }

    fun sendPasswordReset(email: String) {
        val e = email.trim()
        if (e.isBlank()) {
            viewModelScope.launch { setError(getString(Res.string.auth_error_missing_email_for_reset)) }
            return
        }
        launchAuth {
            repository.sendPasswordReset(e)
                .onSuccess {
                    setInfo(getString(Res.string.auth_info_password_reset_sent, e))
                    _pendingPasswordResetEmail.value = e
                }
                .onFailure { setError(it.message ?: getString(Res.string.auth_error_password_reset_failed)) }
        }
    }

    /** Clears the pending password-reset code request, e.g. when the user backs out of that screen. */
    fun clearPendingPasswordReset() {
        _pendingPasswordResetEmail.value = null
    }

    /** Requests a new password-reset code for the pending reset email. */
    fun resendPasswordResetOtp() {
        val email = _pendingPasswordResetEmail.value ?: return
        launchAuth {
            repository.sendPasswordReset(email)
                .onSuccess { setInfo(getString(Res.string.auth_info_otp_resent)) }
                .onFailure { setError(it.message ?: getString(Res.string.auth_error_otp_resend_failed)) }
        }
    }

    /**
     * Verifies the 6-digit code sent to the pending reset email. Success establishes a session
     * and moves the flow into [isRecoveryPending] so the caller can prompt for a new password.
     */
    fun verifyPasswordResetOtp(token: String) {
        val email = _pendingPasswordResetEmail.value ?: return
        val code = token.trim()
        viewModelScope.launch {
            val problem = when {
                code.length != 6 || code.any { !it.isDigit() } -> getString(Res.string.auth_error_invalid_otp)
                else -> null
            }
            if (problem != null) return@launch setError(problem)
            launchAuth {
                repository.verifyPasswordResetOtp(email, code)
                    .onSuccess {
                        clearPendingPasswordReset()
                        _isRecoveryPending.value = true
                    }
                    .onFailure { setError(it.message ?: getString(Res.string.auth_error_otp_failed)) }
            }
        }
    }

    /** Sets the new password to complete a password-reset flow, clearing [isRecoveryPending] on success. */
    fun setNewPassword(newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            val problem = when {
                newPassword.length < 6 -> getString(Res.string.auth_error_password_too_short)
                newPassword != confirmPassword -> getString(Res.string.auth_error_passwords_mismatch)
                else -> null
            }
            if (problem != null) return@launch setError(problem)
            launchAuth {
                repository.updatePassword(newPassword)
                    .onSuccess { _isRecoveryPending.value = false }
                    .onFailure { setError(it.message ?: getString(Res.string.auth_error_password_update_failed)) }
            }
        }
    }

    fun signOut() = launchAuth { repository.signOut() }

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _screenState.value = AuthScreenState(isLoading = true)
            block()
            _screenState.value = _screenState.value.copy(isLoading = false)
        }
    }

    private fun setError(message: String) {
        _screenState.value = AuthScreenState(errorMessage = message)
    }

    private fun setInfo(message: String) {
        _screenState.value = AuthScreenState(infoMessage = message)
    }
}
