package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.platform.integration.RegistryUnavailableException;
import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Polish court register. The open API searches by KRS number only, so it runs after Biała lista
 * and uses the KRS number found there. Sole traders (JDG) have no KRS number and are skipped.
 */
@Component
@ConditionalOnProperty(name = "dealos.registry.live", havingValue = "true", matchIfMissing = true)
class KrsClient implements RegistryClient {

    private final RestClient client;
    private final RegistryHttp http;
    private final Clock clock;

    KrsClient(RegistryHttp http, Clock clock) {
        this.http = http;
        this.client = http.client(http.properties().krs().baseUrl());
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.KRS;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(LookupRequest request) {
        return "PL".equals(request.country());
    }

    @Override
    public Optional<RegistrySnapshot> lookup(LookupRequest request) {
        Optional<String> krs = request.earlier(CheckSource.BIALA_LISTA)
                .map(RegistrySnapshot::krsNumber)
                .filter(k -> k.matches("\\d{1,10}"))
                .map(k -> "0".repeat(10 - k.length()) + k);
        if (krs.isEmpty()) {
            return Optional.empty();
        }
        try {
            String raw = ResilientCall.execute("KRS", () -> client.get()
                    .uri("/OdpisAktualny/{krs}?rejestr=P&format=json", krs.get())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                    .body(String.class), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
            return Optional.of(KrsResultMapper.map(raw == null || raw.isBlank() ? java.util.Map.of() : http.parse(raw), raw, clock.instant()));
        } catch (RegistryUnavailableException e) {
            return Optional.of(RegistrySnapshot.unavailable(CheckSource.KRS, clock.instant(), e.getMessage(), null));
        }
    }
}
