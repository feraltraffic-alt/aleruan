package com.aleruan.dealos.evidence.summary;

import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the summary PDF shows; built once per export and rendered in each language. */
public record SummaryModel(String dealNumber, String dealTitle, String customer, String contractor,
                           BigDecimal contractValue, String currency, LocalDateTime generatedAt, String generatedBy,
                           List<EvidenceCheck> checks, List<Item> items, Audit audit, List<PackedFile> files) {

    /** An entry with the archive paths of its files. */
    public record Item(EvidenceEntry entry, List<PackedFile> files) {
    }

    /** A file as written into the archive. */
    public record PackedFile(String path, String sha256, long size, boolean generated) {
    }

    /** The audit extract: how many records, which range, whether the chain holds. */
    public record Audit(int records, Long firstSeq, Long lastSeq, String lastHash, boolean chainValid,
                        long verifiedRecords, Long brokenAtSeq) {
    }
}
