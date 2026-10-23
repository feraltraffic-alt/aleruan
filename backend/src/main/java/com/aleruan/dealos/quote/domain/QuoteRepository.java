package com.aleruan.dealos.quote.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    /** Versions of a deal's quote without the PDF bytes, newest first. */
    @Query("""
            select new com.aleruan.dealos.quote.domain.QuoteSummary(q.id, q.number, q.version, q.language,
                q.vatMode, q.vatRate, q.currency, q.subtotal, q.vatAmount, q.total, q.issuedOn, q.validUntil,
                q.issuedAt, q.issuedBy, q.pdfSha256)
            from Quote q where q.dealId = :dealId order by q.version desc""")
    List<QuoteSummary> summaries(UUID dealId);

    /** Only the PDF bytes, so exporting many quotes does not keep whole entities in memory. */
    @Query("select q.pdf from Quote q where q.id = :id")
    List<byte[]> pdfOf(UUID id);

    default byte[] pdf(UUID id) {
        List<byte[]> found = pdfOf(id);
        return found.isEmpty() ? null : found.get(0);
    }

    @Query("select coalesce(max(q.version), 0) from Quote q where q.dealId = :dealId")
    int lastVersion(UUID dealId);
}
