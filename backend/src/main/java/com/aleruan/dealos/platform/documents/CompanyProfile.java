package com.aleruan.dealos.platform.documents;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The supplier block printed on quotes, protocols and (later) invoices: Aleruan's own details, from configuration. */
@ConfigurationProperties("dealos.company")
public record CompanyProfile(String name, String country, String companyId, String vatNumber, String address,
                             String email, String phone, String web, String bankName, String iban, String swift) {
}
