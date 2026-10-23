/**
 * Document import: scans and PDFs (supplier invoices, old invoices, contracts, CMR) are read by Claude
 * into structured data, a person checks and corrects it, and only then is anything created: the
 * counterparty (checked in the registries), the received invoice, the contract on the deal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Document import",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "platform::files",
                "platform::documents", "counterparty", "deal", "invoice"})
package com.aleruan.dealos.docimport;
