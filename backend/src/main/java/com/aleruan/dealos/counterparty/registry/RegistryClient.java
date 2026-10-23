package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;

/** One public registry. Implementations never throw for "registry down": they return an unavailable snapshot. */
public interface RegistryClient {

    CheckSource source();

    /** True if this registry can check a counterparty from {@code country} with the given identifiers. */
    boolean supports(String country, String companyId, String vatNumber);

    RegistrySnapshot lookup(String country, String companyId, String vatNumber);
}
