/**
 * Analytics: margin by deal, customer, subcontractor, work type, direction, country and month in one
 * currency; how sites are doing; risks (counterparties flagged in registries, incomplete evidence packages).
 * Reads other modules only through their public catalogs.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"platform::web", "platform::security", "platform::rates", "platform::evidence", "deal",
                "site", "counterparty", "evidence"})
package com.aleruan.dealos.analytics;
