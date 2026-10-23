package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.integration.RegistryUnavailableException;
import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.time.Clock;
import org.springframework.http.HttpStatusCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Czech ARES business register (REST v3, no API key, ~500 requests/min per user). */
@Component
@ConditionalOnProperty(name = "dealos.registry.live", havingValue = "true", matchIfMissing = true)
class AresClient implements RegistryClient {

    private final RegistryHttp http;
    private final RestClient client;
    private final Clock clock;

    AresClient(RegistryHttp http, Clock clock) {
        this.http = http;
        this.client = http.client(http.properties().ares().baseUrl());
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.ARES;
    }

    @Override
    public boolean supports(String country, String companyId, String vatNumber) {
        return "CZ".equals(country) && TaxIdentifiers.isValidCzIco(companyId);
    }

    @Override
    public RegistrySnapshot lookup(String country, String companyId, String vatNumber) {
        String ico = TaxIdentifiers.normalize(companyId);
        try {
            String raw = ResilientCall.execute("ARES", () -> client.get()
                    .uri("/ekonomicke-subjekty/{ico}", ico)
                    .retrieve()
                    // 404 carries {"kod":"NENALEZENO"}: read the body instead of throwing
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> { })
                    .body(String.class), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
            return AresResultMapper.map(http.parse(raw), raw, clock.instant());
        } catch (RegistryUnavailableException e) {
            return RegistrySnapshot.unavailable(CheckSource.ARES, clock.instant(), e.getMessage(), null);
        }
    }
}
