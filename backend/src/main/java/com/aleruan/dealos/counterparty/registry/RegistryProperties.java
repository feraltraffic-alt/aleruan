package com.aleruan.dealos.counterparty.registry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Registry endpoints and the requester identity sent to VIES
 * (Aleruan's own VAT number, so VIES returns a consultation number as evidence).
 */
@ConfigurationProperties("dealos.registry")
public record RegistryProperties(
        @DefaultValue("5s") Duration timeout,
        @DefaultValue Vies vies,
        @DefaultValue Ares ares,
        @DefaultValue BialaLista bialaLista,
        @DefaultValue Krs krs,
        @DefaultValue RegistrDph registrDph) {

    public record Vies(
            @DefaultValue("https://ec.europa.eu/taxation_customs/vies/rest-api") String baseUrl,
            @DefaultValue("CZ") String requesterCountry,
            @DefaultValue("14220997") String requesterNumber) {
    }

    public record Ares(
            @DefaultValue("https://ares.gov.cz/ekonomicke-subjekty-v-be/rest") String baseUrl) {
    }

    public record BialaLista(
            @DefaultValue("https://wl-api.mf.gov.pl/api") String baseUrl) {
    }

    public record Krs(
            @DefaultValue("https://api-krs.ms.gov.pl/api/krs") String baseUrl) {
    }

    public record RegistrDph(
            @DefaultValue("https://adisrws.mfcr.cz/adistc/axis2/services/rozhraniCRPDPH.rozhraniCRPDPHSOAP") String url) {
    }
}
