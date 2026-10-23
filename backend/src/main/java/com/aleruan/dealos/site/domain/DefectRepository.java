package com.aleruan.dealos.site.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DefectRepository extends JpaRepository<Defect, UUID> {

    List<Defect> findBySiteIdOrderBySeqDesc(UUID siteId);

    @Query("select coalesce(max(d.seq), 0) from Defect d where d.siteId = :siteId")
    int lastSeq(@Param("siteId") UUID siteId);

    /** Open and fixed-but-unchecked defects across all sites, most urgent first. */
    @Query("select d from Defect d where d.status in :statuses order by d.dueDate nulls last, d.seq")
    List<Defect> withStatus(@Param("statuses") List<DefectStatus> statuses);

    long countBySiteIdAndStatusIn(UUID siteId, List<DefectStatus> statuses);
}
