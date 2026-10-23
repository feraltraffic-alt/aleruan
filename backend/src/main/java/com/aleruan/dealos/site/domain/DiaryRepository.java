package com.aleruan.dealos.site.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<DiaryEntry, UUID> {

    List<DiaryEntry> findBySiteIdOrderByEntryDateDescCreatedAtDesc(UUID siteId);
}
