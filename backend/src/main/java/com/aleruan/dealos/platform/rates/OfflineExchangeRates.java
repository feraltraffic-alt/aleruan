package com.aleruan.dealos.platform.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fixed rates for tests and offline development ({@code dealos.rates.live=false}). */
@Component
@ConditionalOnProperty(name = "dealos.rates.live", havingValue = "false")
class OfflineExchangeRates implements ExchangeRates {


    private final Map<String, BigDecimal> rates;

    OfflineExchangeRates(org.springframework.core.env.Environment env) {
        this.rates = Map.of(
                "EUR", new BigDecimal(env.getProperty("dealos.rates.offline.EUR", "25.000")),
                "PLN", new BigDecimal(env.getProperty("dealos.rates.offline.PLN", "5.900")));
    }

    @Override
    public Rate rate(String currency, LocalDate day) {
        String code = currency.toUpperCase(Locale.ROOT);
        if ("CZK".equals(code)) {
            return new Rate("CZK", BigDecimal.ONE, day, "CZK");
        }
        BigDecimal r = rates.get(code);
        if (r == null) {
            throw new RatesUnavailableException("No offline rate for " + code, null);
        }
        return new Rate(code, r, day, "offline");
    }
}
