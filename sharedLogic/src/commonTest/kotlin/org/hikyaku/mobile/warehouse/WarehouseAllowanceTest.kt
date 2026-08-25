package org.hikyaku.mobile.warehouse

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarehouseAllowanceTest {

    @Test
    fun canAddWarehouse_personalOrgWithNoWarehouses_allowsAdding() {
        assertTrue(canAddWarehouse(isPersonalOrg = true, existingWarehouseCount = 0))
    }

    @Test
    fun canAddWarehouse_personalOrgAtLimit_blocksAdding() {
        assertFalse(canAddWarehouse(isPersonalOrg = true, existingWarehouseCount = 1))
    }

    @Test
    fun canAddWarehouse_personalOrgOverLimit_blocksAdding() {
        // A grandfathered org that already exceeded the cap before it existed.
        assertFalse(canAddWarehouse(isPersonalOrg = true, existingWarehouseCount = 2))
    }

    @Test
    fun canAddWarehouse_companyOrgIsUnlimited() {
        assertTrue(canAddWarehouse(isPersonalOrg = false, existingWarehouseCount = 50))
    }
}
