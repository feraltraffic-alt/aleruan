package com.aleruan.dealos.invoice.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceSeriesRepository extends JpaRepository<InvoiceSeries, String> {

    /** Creates the series row if missing; safe when two issues race for a new year. */
    @Modifying
    @Query(value = "insert into invoice_series (id, last) values (:id, 0) on conflict (id) do nothing", nativeQuery = true)
    void ensure(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSeries s where s.id = :id")
    Optional<InvoiceSeries> lock(@Param("id") String id);
}
