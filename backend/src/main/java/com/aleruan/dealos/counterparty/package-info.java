/**
 * Counterparties (customers, subcontractors, suppliers, carriers) and their checks
 * against public registries (VIES, ARES, Registr DPH, ISIR, Biała lista, KRS, CEIDG, EU sanctions).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Counterparty",
        allowedDependencies = {"platform::audit", "platform::integration", "platform::web"})
package com.aleruan.dealos.counterparty;
