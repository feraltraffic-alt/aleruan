package com.aleruan.dealos.counterparty.sanctions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SanctionAliasRepository extends JpaRepository<SanctionAlias, Long> {

    List<SanctionAlias> findByNormalizedName(String normalizedName);

    @Modifying
    @Query("delete from SanctionAlias")
    void deleteAllInBulk();
}

interface SanctionImportRepository extends JpaRepository<SanctionImport, Long> {

    Optional<SanctionImport> findTopByOrderByImportedAtDesc();
}
