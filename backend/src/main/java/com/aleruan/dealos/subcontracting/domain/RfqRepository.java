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

    /** Only the id, so the tender can be loaded fresh under a lock afterwards. */
    @Query("select i.rfq.id from RfqInvitation i where i.tokenHash = :hash")
    Optional<UUID> rfqIdByTokenHash(@Param("hash") String hash);

    /**
     * Loads a tender with a row lock, so an award and a last-minute offer from a subcontractor are applied
     * one after the other and the costs written to the deal always match the stored winning offer.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rfq r where r.id = :id")
    Optional<Rfq> lockById(@Param("id") UUID id);
}
