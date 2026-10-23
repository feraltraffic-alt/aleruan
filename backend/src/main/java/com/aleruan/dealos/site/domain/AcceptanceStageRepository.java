package com.aleruan.dealos.site.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcceptanceStageRepository extends JpaRepository<AcceptanceStage, UUID> {

    List<AcceptanceStage> findBySiteIdOrderBySeq(UUID siteId);

    @Query("select coalesce(max(s.seq), 0) from AcceptanceStage s where s.siteId = :siteId")
    int lastSeq(@Param("siteId") UUID siteId);
}
