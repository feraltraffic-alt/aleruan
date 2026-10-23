package com.aleruan.dealos.deal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    @Query("""
            select d from Deal d
            where (:status is null or d.status = :status)
              and (:customerId is null or d.customerId = :customerId)
              and (:q is null or lower(d.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(d.number) like lower(concat('%', cast(:q as string), '%')))
            order by d.createdAt desc
            """)
    List<Deal> search(@Param("q") String q, @Param("status") DealStatus status, @Param("customerId") UUID customerId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "lines")
    @Query("select d from Deal d where d.status in :statuses order by d.createdAt")
    List<Deal> withStatus(@Param("statuses") java.util.Collection<DealStatus> statuses);

    @Query(value = "select nextval('deal_number_seq')", nativeQuery = true)
    long nextNumber();
}
