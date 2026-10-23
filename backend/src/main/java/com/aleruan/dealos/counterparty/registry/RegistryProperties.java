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
        @DefaultValue Ares ares) {

    public record Vies(
            @DefaultValue("https://ec.europa.eu/taxation_customs/vies/rest-api") String baseUrl,
            @DefaultValue("CZ") String requesterCountry,
            @DefaultValue("14220997") String requesterNumber) {
    }

    public record Ares(
            @DefaultValue("https://ares.gov.cz/ekonomicke-subjekty-v-be/rest") String baseUrl) {
    }
}
