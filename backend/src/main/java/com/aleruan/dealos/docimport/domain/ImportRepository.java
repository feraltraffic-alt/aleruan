package com.aleruan.dealos.docimport.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportRepository extends JpaRepository<ImportDocument, UUID> {

    @Query("""
            select d from ImportDocument d where (:status is null or d.status = :status)
            order by d.uploadedAt desc""")
    List<ImportDocument> search(@Param("status") ImportStatus status);

    /** The oldest queued document, locked so that two workers never read the same one. */
    @Query(value = """
            select id from import_document where status = 'QUEUED'
            order by uploaded_at limit 1 for update skip locked""", nativeQuery = true)
    Optional<UUID> lockNextQueued();

    /** Documents whose reading never finished (the server stopped mid-way) go back to the queue. */
    @Modifying
    @Query("update ImportDocument d set d.status = com.aleruan.dealos.docimport.domain.ImportStatus.QUEUED "
            + "where d.status = com.aleruan.dealos.docimport.domain.ImportStatus.PROCESSING and d.processedAt < :before")
    int requeueStale(@Param("before") Instant before);

    Optional<ImportDocument> findFirstBySha256AndStatusNot(String sha256, ImportStatus status);

    long countByStatus(ImportStatus status);
}
