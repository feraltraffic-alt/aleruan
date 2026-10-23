package com.aleruan.dealos.counterparty;

import java.util.Set;
import java.util.UUID;

/** What other modules may know about a counterparty. Roles and status are plain strings to keep modules decoupled. */
public record CounterpartySummary(UUID id, String legalName, String country, Set<String> roles,
                                  Set<String> workTypes, String checkStatus,
                                  String companyId, String vatNumber, String address) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isRed() {
        return "RED".equals(checkStatus);
    }
}
