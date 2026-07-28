package org.hikyaku.mobile.organisation

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.organisation.model.Organisation
import org.hikyaku.mobile.supabase.SupabaseTables

class OrganisationRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    /**
     * Returns the organisations visible to the signed-in user. Row Level Security on
     * the backend restricts the result to organisations the user created or is a
     * member of, so no client-side filtering is required.
     */
    suspend fun fetchOrganisations(): Result<List<Organisation>> = runCatching {
        client.postgrest.from(SupabaseTables.ORGANISATIONS)
            .select()
            .decodeList<Organisation>()
    }

    /**
     * Creates the signed-in user's personal organisation if they don't already have one.
     * `created_by`/`slug`/`id` are left to their column defaults (`auth.uid()`, a random
     * slug, `gen_random_uuid()`), matching how the backend provisions personal orgs.
     * Safe to call on every sign-in: a no-op once the personal org exists.
     */
    suspend fun ensurePersonalOrganisation(): Result<Unit> = runCatching {
        val hasPersonalOrg = client.postgrest.from(SupabaseTables.ORGANISATIONS)
            .select(Columns.raw("id")) { filter { eq("org_type", "personal") } }
            .decodeList<OrganisationIdRow>()
            .isNotEmpty()
        if (!hasPersonalOrg) {
            client.postgrest.from(SupabaseTables.ORGANISATIONS).insert(PersonalOrganisationInsert())
        }
    }
}

@Serializable
private data class OrganisationIdRow(val id: String)

@Serializable
private data class PersonalOrganisationInsert(@SerialName("org_type") val orgType: String = "personal")
