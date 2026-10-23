/**
 * Sites (объекты): where a won deal is carried out — address and map position, project manager and
 * foreman, the work schedule by subcontractor, and the load on each subcontractor across sites.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Site",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "platform::documents", "platform::files", "deal",
                "counterparty"})
package com.aleruan.dealos.site;
