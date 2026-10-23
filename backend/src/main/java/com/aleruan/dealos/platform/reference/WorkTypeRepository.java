package com.aleruan.dealos.platform.reference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface WorkTypeRepository extends JpaRepository<WorkType, UUID> {

    List<WorkType> findByActiveTrueOrderByCode();

    boolean existsByCode(String code);
}
