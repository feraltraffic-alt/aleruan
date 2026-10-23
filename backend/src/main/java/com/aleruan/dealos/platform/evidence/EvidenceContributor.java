package com.aleruan.dealos.platform.evidence;

import java.util.UUID;

/**
 * A module's share of a deal's evidence package. Implementations must not load file contents while
 * contributing: contents are read through {@link EvidenceFile#content()} only when the package is exported.
 */
public interface EvidenceContributor {

    void contribute(UUID dealId, EvidenceCollector out);
}
