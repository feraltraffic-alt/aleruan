package com.aleruan.dealos.platform.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** CZK per unit of a foreign currency, as announced by the ČNB for a given day. */
public interface ExchangeRates {

    /**
     * @param validFor the ČNB announcement date the rate comes from (the last one on or before the asked day)
     */
    record Rate(String currency, BigDecimal czkPerUnit, LocalDate validFor, String source) {

        public BigDecimal toCzk(BigDecimal amount) {
            return amount.multiply(czkPerUnit).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** @throws RatesUnavailableException when the ČNB cannot be reached or does not quote the currency */
    Rate rate(String currency, LocalDate day);
}
