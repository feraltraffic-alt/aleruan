/**
 * Subcontracting: tenders (RFQ) for deal lines, offers from subcontractors through a private link,
 * comparison and award. The award writes the winning cost back to the deal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Subcontracting",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "counterparty", "deal"})
package com.aleruan.dealos.subcontracting;
