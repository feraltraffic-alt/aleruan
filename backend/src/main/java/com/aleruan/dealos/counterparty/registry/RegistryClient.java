package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.util.Optional;

/**
 * One public registry. Implementations never throw for "registry down": they return an unavailable snapshot.
 * An empty result means "nothing to check here" (e.g. KRS for a sole trader who has no KRS number).
 */
public interface RegistryClient {

    CheckSource source();

    /** Registries run in ascending order, so later ones can use earlier snapshots. */
    default int order() {
        return 100;
    }

    boolean supports(LookupRequest request);

    Optional<RegistrySnapshot> lookup(LookupRequest request);
}
