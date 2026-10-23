package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.integration.RegistryUnavailableException;
import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Polish VAT white list. Daily request limits apply (stricter for search than for account checks),
 * so it is called per counterparty check, not in bulk.
 */
@Component
@ConditionalOnProperty(name = "dealos.registry.live", havingValue = "true", matchIfMissing = true)
public class BialaListaClient implements RegistryClient {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final RegistryHttp http;
    private final RestClient client;
    private final Clock clock;

    BialaListaClient(RegistryHttp http, Clock clock) {
        this.http = http;
        this.client = http.client(http.properties().bialaLista().baseUrl());
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.BIALA_LISTA;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(LookupRequest request) {
        return "PL".equals(request.country()) && TaxIdentifiers.isValidPlNip(request.companyId());
    }

    @Override
    public Optional<RegistrySnapshot> lookup(LookupRequest request) {
        String nip = TaxIdentifiers.normalize(request.companyId());
        return Optional.of(get("/search/nip/{nip}?date={date}", raw -> BialaListaResultMapper.mapSearch(http.parse(raw), raw, clock.instant()),
                nip, today()));
    }

    /** Is this account assigned to this NIP on the white list today? */
    public RegistrySnapshot checkAccount(String nip, String nrb) {
        return get("/check/nip/{nip}/bank-account/{nrb}?date={date}",
                raw -> BialaListaResultMapper.mapAccountCheck(http.parse(raw), raw, clock.instant(), nrb),
                TaxIdentifiers.normalize(nip), nrb, today());
    }

    private RegistrySnapshot get(String uri, java.util.function.Function<String, RegistrySnapshot> mapper, Object... vars) {
        try {
            String raw = ResilientCall.execute("Biała lista", () -> client.get()
                    .uri(uri, vars)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { }) // error body is JSON
                    .body(String.class), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
            return mapper.apply(raw);
        } catch (RegistryUnavailableException e) {
            return RegistrySnapshot.unavailable(CheckSource.BIALA_LISTA, clock.instant(), e.getMessage(), null);
        }
    }

    private String today() {
        return LocalDate.now(clock.withZone(WARSAW)).toString();
    }
}
