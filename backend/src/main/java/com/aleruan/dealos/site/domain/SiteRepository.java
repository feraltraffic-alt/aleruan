package com.aleruan.dealos.site.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    Optional<Site> findByDealId(UUID dealId);

    boolean existsByDealId(UUID dealId);

    @Query("""
            select s from Site s
            where (:status is null or s.status = :status)
              and (:country is null or s.country = cast(:country as string))
              and (:city is null or lower(s.city) = lower(cast(:city as string)))
              and (:pm is null or lower(s.projectManager) = lower(cast(:pm as string)))
              and (:dealId is null or s.dealId = :dealId)
            order by s.plannedStart nulls last, s.name""")
    List<Site> search(@Param("status") SiteStatus status, @Param("country") String country, @Param("city") String city,
                      @Param("pm") String pm, @Param("dealId") UUID dealId);

    /** Tasks with a subcontractor that overlap the window [from, to]. */
    @Query("""
            select t from SiteTask t join fetch t.site
            where t.subcontractorId is not null and t.start <= :to and t.end >= :from
            order by t.start""")
    List<SiteTask> tasksBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
