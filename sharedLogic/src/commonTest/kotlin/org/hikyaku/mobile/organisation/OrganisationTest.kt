package org.hikyaku.mobile.organisation

import org.hikyaku.mobile.organisation.model.Organisation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrganisationTest {

    private fun organisation(orgType: String, logoUrl: String?) = Organisation(
        id = "org-1",
        name = "Acme Logistics",
        slug = "acme-logistics",
        orgType = orgType,
        createdBy = "user-1",
        logoUrl = logoUrl,
    )

    @Test
    fun brandingLogoUrl_companyWithALogo_isTheLogo() {
        val org = organisation(orgType = "company", logoUrl = "https://cdn.example.com/acme.png")
        assertEquals("https://cdn.example.com/acme.png", org.brandingLogoUrl)
    }

    @Test
    fun brandingLogoUrl_companyWithoutALogo_isNull() {
        assertNull(organisation(orgType = "company", logoUrl = null).brandingLogoUrl)
        assertNull(organisation(orgType = "company", logoUrl = "   ").brandingLogoUrl)
    }

    @Test
    fun brandingLogoUrl_personalOrgIsNeverBranded() {
        val org = organisation(orgType = "personal", logoUrl = "https://cdn.example.com/jane.png")
        assertNull(org.brandingLogoUrl)
    }
}
