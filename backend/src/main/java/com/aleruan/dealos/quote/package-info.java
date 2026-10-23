/**
 * Quotes (КП / cenová nabídka): an immutable, versioned customer offer generated from a deal's prices,
 * with the supplier's details, a VAT treatment and a PDF that is stored exactly as it was sent.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Quote",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "platform::documents", "deal",
                "counterparty"})
package com.aleruan.dealos.quote;
