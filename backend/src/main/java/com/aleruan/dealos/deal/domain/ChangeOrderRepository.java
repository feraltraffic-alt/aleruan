package com.aleruan.dealos.deal.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeOrderRepository extends JpaRepository<ChangeOrder, UUID> {

    List<ChangeOrder> findByDealIdOrderBySeq(UUID dealId);

    @Query("select coalesce(max(c.seq), 0) from ChangeOrder c where c.dealId = :dealId")
    int lastSeq(@Param("dealId") UUID dealId);
}
