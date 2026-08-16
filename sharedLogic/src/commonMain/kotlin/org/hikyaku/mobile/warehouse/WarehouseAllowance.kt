package org.hikyaku.mobile.warehouse

/**
 * Personal accounts get one warehouse; company orgs are unlimited.
 *
 * Mirrors the `warehouse_personal_org_limit` trigger in hikyaku-api (migration
 * 1786790000000-limit_personal_org_warehouses.sql) — that trigger is the actual enforcement
 * point, since every warehouse insert here (from [org.hikyaku.mobile.shift.create.CreateShiftRepository]
 * and [org.hikyaku.mobile.packages.PackageRepository]) goes straight to `public.warehouse` via
 * PostgREST. This only decides whether those flows offer "add a new warehouse" — keep it in step
 * with that migration's `v_limit`.
 */
const val PERSONAL_ORG_WAREHOUSE_LIMIT = 1

/** Whether an org that already has [existingWarehouseCount] warehouses may add another. */
fun canAddWarehouse(isPersonalOrg: Boolean, existingWarehouseCount: Int): Boolean =
    !isPersonalOrg || existingWarehouseCount < PERSONAL_ORG_WAREHOUSE_LIMIT
