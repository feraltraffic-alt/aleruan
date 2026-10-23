package com.aleruan.dealos.platform.files;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    @Query("""
            select new com.aleruan.dealos.platform.files.FileInfo(f.id, f.ownerType, f.ownerId, f.fileName,
                f.contentType, f.sizeBytes, f.sha256, f.uploadedAt, f.uploadedBy)
            from StoredFile f where f.ownerType = :type and f.ownerId in :ids order by f.uploadedAt""")
    List<FileInfo> infos(@Param("type") String type, @Param("ids") List<UUID> ids);

    @Query("select count(f) from StoredFile f where f.ownerType = :type and f.ownerId = :id")
    long countFor(@Param("type") String type, @Param("id") UUID id);
}
