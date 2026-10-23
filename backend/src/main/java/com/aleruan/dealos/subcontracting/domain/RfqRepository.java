package com.aleruan.dealos.subcontracting.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RfqRepository extends JpaRepository<Rfq, UUID> {

    List<Rfq> findByDealIdOrderByCreatedAtDesc(UUID dealId);

    @Query("select i.rfq from RfqInvitation i where i.tokenHash = :hash")
    Optional<Rfq> findByTokenHash(@Param("hash") String hash);
}
