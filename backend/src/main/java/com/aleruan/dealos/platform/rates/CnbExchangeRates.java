package com.aleruan.dealos.platform.rates;

import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Live ČNB daily fixing; one download per day is cached (rates for a past day never change). */
@Component
@ConditionalOnProperty(name = "dealos.rates.live", havingValue = "true", matchIfMissing = true)
class CnbExchangeRates implements ExchangeRates {

    private static final DateTimeFormatter QUERY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<LocalDate, String> cache = new ConcurrentHashMap<>();
    private final String url;

    CnbExchangeRates(@Value("${dealos.rates.cnb-url:https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt}") String url) {
        this.url = url;
    }

    @Override
    public Rate rate(String currency, LocalDate day) {
        if ("CZK".equalsIgnoreCase(currency)) {
            return new Rate("CZK", BigDecimal.ONE, day, "CZK");
        }
        String text;
        try {
            // today's fixing appears at 14:30, so only finished days are cached
            text = day.isBefore(LocalDate.now(java.time.ZoneId.of("Europe/Prague"))) ? cache.computeIfAbsent(day, this::fetch) : fetch(day);
        } catch (RuntimeException e) {
            throw new RatesUnavailableException("ČNB exchange rates are not available right now", e);
        }
        return CnbRatesParser.find(text, currency)
                .orElseThrow(() -> new RatesUnavailableException("ČNB does not quote " + currency.toUpperCase(Locale.ROOT), null));
    }

    private String fetch(LocalDate day) {
        return ResilientCall.execute("ČNB", () -> download(day), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
    }

    private String download(LocalDate day) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "?date=" + day.format(QUERY)))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IllegalStateException("ČNB answered " + res.statusCode());
        }
        return res.body();
    }
}
