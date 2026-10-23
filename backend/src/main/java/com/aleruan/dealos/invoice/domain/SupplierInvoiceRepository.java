package com.aleruan.dealos.invoice.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {

    @Query("""
            select s from SupplierInvoice s
            where (:dealId is null or s.dealId = :dealId)
              and (:supplierId is null or s.supplierId = :supplierId)
            order by s.issueDate desc nulls last, s.createdAt desc""")
    List<SupplierInvoice> search(@Param("dealId") UUID dealId, @Param("supplierId") UUID supplierId);

    boolean existsBySupplierIdAndNumberIgnoreCase(UUID supplierId, String number);
}
