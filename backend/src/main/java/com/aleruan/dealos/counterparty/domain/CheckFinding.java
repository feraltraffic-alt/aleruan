package com.aleruan.dealos.counterparty.domain;

/** A single reason a counterparty is not green. */
public enum CheckFinding {
    VAT_INVALID(CheckStatus.RED),
    NOT_FOUND_IN_REGISTRY(CheckStatus.RED),
    UNRELIABLE_VAT_PAYER(CheckStatus.RED),
    VAT_NOT_ACTIVE(CheckStatus.RED),
    INSOLVENCY(CheckStatus.RED),
    LIQUIDATION_OR_CEASED(CheckStatus.RED),
    SANCTIONS_HIT(CheckStatus.RED),
    YOUNG_COMPANY(CheckStatus.YELLOW),
    NOT_VAT_PAYER(CheckStatus.YELLOW),
    SANCTIONS_POSSIBLE_MATCH(CheckStatus.YELLOW),
    ACCOUNT_NOT_PUBLISHED(CheckStatus.YELLOW),
    SOURCE_UNAVAILABLE(CheckStatus.YELLOW),
    MANUAL_REVIEW_REQUIRED(CheckStatus.YELLOW);

    private final CheckStatus severity;

    CheckFinding(CheckStatus severity) {
        this.severity = severity;
    }

    public CheckStatus severity() {
        return severity;
    }
}
