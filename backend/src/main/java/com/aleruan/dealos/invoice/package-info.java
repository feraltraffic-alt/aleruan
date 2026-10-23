/**
 * Invoicing: tax documents to customers from accepted stages, deal lines or by hand, with Czech VAT
 * rules (DUZP, CZK VAT at the ČNB rate), gap-free numbering, payment QR codes, payments and credit notes.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Invoice",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "platform::documents",
                "platform::rates", "deal", "counterparty", "site"})
package com.aleruan.dealos.invoice;
