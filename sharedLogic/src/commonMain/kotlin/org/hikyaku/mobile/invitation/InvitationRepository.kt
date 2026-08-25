package org.hikyaku.mobile.invitation

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.invitation.model.Invitation
import org.hikyaku.mobile.net.ApiConfigProvider
import org.hikyaku.mobile.net.ApiEndpoints
import org.hikyaku.mobile.net.ApiHeaders
import org.hikyaku.mobile.net.appHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * The invitee side of team invitations: checking for, accepting, and declining invitations
 * addressed to the signed-in user's own verified email. Sending invitations is an org-admin
 * action that stays on the web dashboard, so that endpoint has no client here.
 *
 * `GET /invitations/pending`, `POST /invitations/{id}/accept` and `.../decline` aren't
 * documented with request/response schemas in the pinned OpenAPI spec (only the web-only
 * `POST /invitations` create endpoint is), so unlike the app's other `hikyaku-api` repositories
 * this one's wire DTOs are hand-written below rather than generated — there's nothing for the
 * swagger plugin to generate from. Shapes are taken from the `hikyaku`/`hikyaku-api` source.
 */
class InvitationRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val httpClient: HttpClient = appHttpClient,
    private val apiUrl: () -> String = { ApiConfigProvider.requireUrl },
) {
    /** Invitations awaiting the caller's decision, newest first, empty when there are none. */
    suspend fun fetchPending(): Result<List<Invitation>> = runCatching {
        val response = httpClient.get(ApiEndpoints.invitationsPending(apiUrl())) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
        }
        if (response.status.value !in 200..299) {
            error("Failed to load invitations (${response.status.value})")
        }
        response.body<List<PendingInvitationDto>>().map { it.toInvitation() }
    }.onFailure {
        if (it is CancellationException) throw it
    }

    /**
     * Accepts [invitationId], atomically creating the caller's membership server-side. Returns
     * the organisation to switch into. Throws [EmailNotVerifiedException] (403) or
     * [InvitationUnavailableException] (404 — unknown id, already resolved, or not addressed to
     * the caller's email; not distinguished further by the server) instead of a generic failure,
     * so callers can show the right message. Not idempotent: calling this twice 404s the second
     * time.
     */
    suspend fun accept(invitationId: String): Result<AcceptedInvitation> = runCatching {
        val response = httpClient.post(ApiEndpoints.invitationAccept(apiUrl(), invitationId)) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
        }
        when {
            response.status.value in 200..299 -> response.body<AcceptInvitationResponseDto>().let {
                AcceptedInvitation(organisationId = it.organisationId, organisationSlug = it.organisationSlug)
            }
            response.status.value == 403 -> throw EmailNotVerifiedException()
            response.status.value == 404 -> throw InvitationUnavailableException()
            else -> error("Failed to accept invitation (${response.status.value}): ${response.bodyAsText().take(300)}")
        }
    }.onFailure {
        if (it is CancellationException) throw it
    }

    /** Declines [invitationId]; no membership is created. Same 404-on-repeat-call behavior as [accept]. */
    suspend fun decline(invitationId: String): Result<Unit> = runCatching {
        val response = httpClient.post(ApiEndpoints.invitationDecline(apiUrl(), invitationId)) {
            header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken()))
        }
        when {
            response.status.value in 200..299 -> Unit
            response.status.value == 404 -> throw InvitationUnavailableException()
            else -> error("Failed to decline invitation (${response.status.value}): ${response.bodyAsText().take(300)}")
        }
    }.onFailure {
        if (it is CancellationException) throw it
    }

    private fun accessToken(): String =
        client.auth.currentSessionOrNull()?.accessToken ?: error("Session expired. Please sign in again.")

    private fun PendingInvitationDto.toInvitation() = Invitation(
        id = id,
        organisationId = organisation.id,
        organisationSlug = organisation.slug,
        organisationName = organisation.name,
        role = role,
        permissions = permissions,
    )
}

/** Thrown by [InvitationRepository.accept] when the caller's email isn't verified yet (403). */
class EmailNotVerifiedException : Exception("Verify your email address before accepting the invitation.")

/** Thrown when the invitation id is unknown, already resolved, or not addressed to the caller (404). */
class InvitationUnavailableException : Exception("This invitation is no longer available.")

/** The organisation [InvitationRepository.accept] created the caller's membership in. */
data class AcceptedInvitation(val organisationId: String, val organisationSlug: String)

@Serializable
private data class PendingInvitationDto(
    val id: String,
    val organisation: InvitationOrganisationDto,
    val role: String,
    val permissions: List<String> = emptyList(),
)

@Serializable
private data class InvitationOrganisationDto(
    val id: String,
    val slug: String,
    val name: String,
)

@Serializable
private data class AcceptInvitationResponseDto(
    @SerialName("organisation_id") val organisationId: String,
    @SerialName("organisation_slug") val organisationSlug: String,
)
