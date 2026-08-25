package org.hikyaku.mobile.net

/**
 * Every HTTP endpoint path the app calls outside of Supabase (Postgrest/Auth/Storage), in one
 * place so a path or version bump doesn't require hunting through each repository. Each function
 * takes the resolved base URL rather than reading [ApiConfigProvider] itself, matching the
 * `apiUrl: () -> String` constructor param already used by these repositories.
 */
object ApiEndpoints {
    /** `GET {baseUrl}/api/environment` — resolves runtime config for a Hikyaku instance. */
    fun environment(baseUrl: String) = "${baseUrl.trimEnd('/')}/api/environment"

    /** `GET {apiUrl}/geocode/autocomplete?text=` — Pelias/Photon address autocomplete. */
    fun geocodeAutocomplete(apiUrl: String) = "${apiUrl.trimEnd('/')}/geocode/autocomplete"

    /**
     * `GET {apiUrl}/geocode/reverse?lat=&lon=&radius=&include=` — reverse geocode / nearby-POI
     * lookup (Photon). Used to find fuel stops (`include=osm.amenity.fuel`) around a point.
     */
    fun geocodeReverse(apiUrl: String) = "${apiUrl.trimEnd('/')}/geocode/reverse"

    /** `POST {apiUrl}/api/v1/routing/route` — road-snapped route preview. */
    fun routingRoute(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/routing/route"

    /**
     * `POST {apiUrl}/api/v1/optimisation/adhoc` — optimises and persists a shift from a
     * vehicle type, start location + time, and the package ids to deliver; returns the
     * `vrp_optimization` id. Used by the mobile create-shift flow.
     */
    fun optimisationAdhoc(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/optimisation/adhoc"

    /**
     * `POST {apiUrl}/api/v1/optimisation/run` — queues a warehouse-wide optimisation run that
     * assigns all of its unassigned packages to routes. Returns immediately with the queued run's
     * id; poll [optimisationRunLatest] for its outcome.
     */
    fun optimisationRun(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/optimisation/run"

    /**
     * `GET {apiUrl}/api/v1/optimisation/run/latest` — the organisation's most recent
     * warehouse-wide optimisation run (or null if it has never run one).
     */
    fun optimisationRunLatest(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/optimisation/run/latest"

    /**
     * `GET {apiUrl}/api/v1/invitations/pending` — team invitations awaiting the caller's
     * decision, matched by their own verified email (not org-scoped, no `X-Organisation-Slug`
     * needed).
     */
    fun invitationsPending(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/pending"

    /**
     * `POST {apiUrl}/api/v1/invitations/{id}/accept` — accepts a pending invitation, creating
     * the caller's `team_members` row in the invitation's organisation.
     */
    fun invitationAccept(apiUrl: String, id: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/$id/accept"

    /** `POST {apiUrl}/api/v1/invitations/{id}/decline` — declines a pending invitation. */
    fun invitationDecline(apiUrl: String, id: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/$id/decline"
}
