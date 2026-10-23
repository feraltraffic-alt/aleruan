package com.aleruan.dealos.counterparty.domain;

/** Traffic light. Declaration order = severity order. */
public enum CheckStatus {
    GREEN, YELLOW, RED;

    public CheckStatus worst(CheckStatus other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
