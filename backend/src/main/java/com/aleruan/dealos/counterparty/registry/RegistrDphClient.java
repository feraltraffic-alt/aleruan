package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.integration.RegistryUnavailableException;
import com.aleruan.dealos.platform.integration.ResilientCall;
import com.aleruan.dealos.platform.integration.RetryPolicy;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Czech VAT register: unreliable payer status and published bank accounts (SOAP, no key). */
@Component
@ConditionalOnProperty(name = "dealos.registry.live", havingValue = "true", matchIfMissing = true)
public class RegistrDphClient implements RegistryClient {

    private final RestClient client;
    private final Clock clock;

    RegistrDphClient(RegistryHttp http, Clock clock) {
        this.client = http.client(http.properties().registrDph().url());
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.REGISTR_DPH;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(LookupRequest request) {
        return "CZ".equals(request.country()) && dicDigits(request) != null;
    }

    @Override
    public Optional<RegistrySnapshot> lookup(LookupRequest request) {
        return Optional.of(status(dicDigits(request)));
    }

    public RegistrySnapshot status(String dicDigits) {
        try {
            String xml = ResilientCall.execute("Registr DPH", () -> client.post()
                    .contentType(MediaType.TEXT_XML)
                    .header("SOAPAction", "getStatusNespolehlivyPlatceRozsireny")
                    .body(RegistrDphResultMapper.request(dicDigits))
                    .retrieve()
                    .body(String.class), RetryPolicy.defaults(), ResilientCall.Sleeper.THREAD);
            return RegistrDphResultMapper.map(xml, clock.instant());
        } catch (RegistryUnavailableException e) {
            return RegistrySnapshot.unavailable(CheckSource.REGISTR_DPH, clock.instant(), e.getMessage(), null);
        }
    }

    /** DIČ digits from the VAT number, or the IČO (a Czech company's DIČ is CZ + IČO). */
    static String dicDigits(LookupRequest r) {
        if (r.vatNumber() != null && !r.vatNumber().isBlank()) {
            TaxIdentifiers.VatId vat = TaxIdentifiers.parseVat(r.vatNumber());
            if ("CZ".equals(vat.country()) && vat.number().matches("\\d{8,10}")) {
                return vat.number();
            }
        }
        return TaxIdentifiers.isValidCzIco(r.companyId()) ? TaxIdentifiers.normalize(r.companyId()) : null;
    }
}
