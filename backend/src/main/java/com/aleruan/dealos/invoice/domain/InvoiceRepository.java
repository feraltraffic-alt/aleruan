package com.aleruan.dealos.invoice.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("""
            select i from Invoice i
            where (:status is null or i.status = :status)
              and (:dealId is null or i.dealId = :dealId)
              and (:customerId is null or i.customerId = :customerId)
            order by coalesce(i.issueDate, i.taxableDate) desc, i.number desc nulls first""")
    List<Invoice> search(@Param("status") InvoiceStatus status, @Param("dealId") UUID dealId,
                         @Param("customerId") UUID customerId);

    /** Stage ids already on a draft or issued invoice that has not been credited. */
    @Query("""
            select l.stageId from Invoice i join i.lines l
            where i.dealId = :dealId and l.stageId is not null and i.kind = :kind and i.status <> :credited""")
    List<UUID> stagesOn(@Param("dealId") UUID dealId, @Param("kind") InvoiceKind kind,
                        @Param("credited") InvoiceStatus credited);

    default List<UUID> invoicedStages(UUID dealId) {
        return stagesOn(dealId, InvoiceKind.INVOICE, InvoiceStatus.CREDITED);
    }
}
