package com.aleruan.dealos.platform.security;

import java.util.Arrays;

/** Realm roles defined in Keycloak (infra/keycloak/realm-aleruan.json). */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String SALES_MANAGER = "SALES_MANAGER";
    public static final String ACCOUNT_MANAGER = "ACCOUNT_MANAGER";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";
    public static final String FOREMAN = "FOREMAN";
    public static final String WAREHOUSE_KEEPER = "WAREHOUSE_KEEPER";

    static final String[] ALL = {ADMIN, SALES_MANAGER, ACCOUNT_MANAGER, ACCOUNTANT, PROJECT_MANAGER, FOREMAN,
            WAREHOUSE_KEEPER};

    static final String[] ALL_AUTHORITIES = Arrays.stream(ALL).map(r -> "ROLE_" + r).toArray(String[]::new);

    private Roles() {
    }
}
