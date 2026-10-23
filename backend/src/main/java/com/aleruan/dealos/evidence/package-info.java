/**
 * Evidence package of a deal: everything the modules hold about it (contract papers, quotes, change
 * orders, acceptance protocols, invoices, site diary, defects with photos, registry checks, audit log
 * extract) collected into one ZIP with a summary PDF and a hash manifest, plus a completeness indicator.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Evidence",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "platform::documents",
                "platform::evidence", "deal", "counterparty"})
package com.aleruan.dealos.evidence;
