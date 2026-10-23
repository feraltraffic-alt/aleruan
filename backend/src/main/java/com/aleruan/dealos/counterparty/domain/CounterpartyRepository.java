package com.aleruan.dealos.counterparty.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CounterpartyRepository extends JpaRepository<Counterparty, UUID> {

    boolean existsByCountryAndCompanyId(String country, String companyId);

    @Query("""
            select distinct c from Counterparty c left join c.roles r
            where (:q is null or lower(c.legalName) like lower(concat('%', cast(:q as string), '%'))
                   or c.companyId like concat('%', cast(:q as string), '%')
                   or c.vatNumber like concat('%', cast(:q as string), '%'))
              and (:role is null or r = :role)
            order by c.legalName
            """)
    List<Counterparty> search(@Param("q") String q, @Param("role") CounterpartyRole role);
}
