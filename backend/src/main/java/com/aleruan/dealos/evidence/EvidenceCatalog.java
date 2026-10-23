package com.aleruan.dealos.evidence;

import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import java.util.List;
import java.util.UUID;

/** Public API of the evidence module: what a deal's package lacks, for analytics. */
public interface EvidenceCatalog {

    /** Completeness checks of the deal's package, gaps first; the audit chain is not checked here. */
    List<EvidenceCheck> gaps(UUID dealId);
}
