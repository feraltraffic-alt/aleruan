package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.integration.RegistryUnavailableException;
import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** EU VIES VAT validation (REST, no API key). */
@Component
@ConditionalOnProperty(name = "dealos.registry.live", havingValue = "true", matchIfMissing = true)
class ViesClient implements RegistryClient {

    private final RegistryHttp http;
    private final RestClient client;
    private final Clock clock;

    ViesClient(RegistryHttp http, Clock clock) {
        this.http = http;
        this.client = http.client(http.properties().vies().baseUrl());
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.VIES;
    }

    @Override
    public boolean supports(String country, String companyId, String vatNumber) {
        return vatNumber != null && !vatNumber.isBlank() && TaxIdentifiers.isViesCountry(country);
    }

    @Override
    public RegistrySnapshot lookup(String country, String companyId, String vatNumber) {
        TaxIdentifiers.VatId vat = TaxIdentifiers.parseVat(vatNumber);
        RegistryProperties.Vies cfg = http.properties().vies();
        Map<String, String> body = Map.of(
                "countryCode", vat.country(),
                "vatNumber", vat.number(),
                "requesterMemberStateCode", cfg.requesterCountry(),
                "requesterNumber", cfg.requesterNumber());
        try {
            String raw = ResilientCall.execute("VIES", () -> client.post()
                    .uri("/check-vat-number")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
            return ViesResultMapper.map(http.parse(raw), raw, clock.instant());
        } catch (RegistryUnavailableException e) {
            return RegistrySnapshot.unavailable(CheckSource.VIES, clock.instant(), e.getMessage(), null);
        }
    }
}
