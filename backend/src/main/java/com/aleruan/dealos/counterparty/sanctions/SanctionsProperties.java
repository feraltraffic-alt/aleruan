package com.aleruan.dealos.counterparty.sanctions;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EU consolidated list download. The FSF URL carries a personal token issued on free registration
 * (https://webgate.ec.europa.eu/fsd/fsf), so it is configured per installation; empty = import disabled.
 */
@ConfigurationProperties("dealos.sanctions")
public record SanctionsProperties(String url) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
