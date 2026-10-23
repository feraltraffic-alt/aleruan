package com.aleruan.dealos.counterparty.sanctions;

/** One name (alias) of a sanctioned person or entity from the EU consolidated list. */
public record SanctionedName(String logicalId, String subjectType, String wholeName) {
}
