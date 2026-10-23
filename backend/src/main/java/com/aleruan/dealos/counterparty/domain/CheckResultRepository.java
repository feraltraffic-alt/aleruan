package com.aleruan.dealos.counterparty.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

    List<CheckResult> findByCounterpartyIdOrderByCheckedAtDesc(UUID counterpartyId);
}
