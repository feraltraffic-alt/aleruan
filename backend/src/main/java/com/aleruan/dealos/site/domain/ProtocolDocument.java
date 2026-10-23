package com.aleruan.dealos.site.domain;

import com.aleruan.dealos.platform.documents.DocLanguage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything printed on a handover protocol for one acceptance stage. */
public record ProtocolDocument(
        String number,
        DocLanguage language,
        LocalDate date,
        Party contractor,
        Party customer,
        String siteName,
        String siteAddress,
        String dealNumber,
        String stageTitle,
        String stageDescription,
        List<String> works,
        BigDecimal amount,
        String currency,
        StageStatus status,
        String decidedBy,
        LocalDate decidedOn,
        String remarks,
        LocalDate fixUntil,
        String handedOverBy) {

    public record Party(String name, String country, String companyId, String vatNumber, String address) {
    }
}
