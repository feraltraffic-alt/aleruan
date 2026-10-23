package com.aleruan.dealos.counterparty.sanctions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SanctionsXmlParserTest {

    static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <export xmlns="http://eu.europa.ec/fpi/fsd/export" generationDate="2026-10-22T18:00:00">
              <sanctionEntity logicalId="13" designationDate="2022-03-15">
                <subjectType code="enterprise" classificationCode="E"/>
                <nameAlias wholeName="OOO Stroyinvest Fiktivny" nameLanguage="EN"/>
                <nameAlias wholeName="ООО «Стройинвест Фиктивный»" nameLanguage="RU"/>
              </sanctionEntity>
              <sanctionEntity logicalId="27">
                <subjectType code="person" classificationCode="P"/>
                <nameAlias firstName="Ivan" lastName="Testovy" wholeName=""/>
              </sanctionEntity>
            </export>""";

    @Test
    void readsAllAliasesWithTheirEntity() throws Exception {
        List<SanctionedName> names = SanctionsXmlParser.parse(new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(
                new SanctionedName("13", "enterprise", "OOO Stroyinvest Fiktivny"),
                new SanctionedName("13", "enterprise", "ООО «Стройинвест Фиктивный»"),
                new SanctionedName("27", "person", "Ivan Testovy")), names);
    }

    @Test
    void refusesDoctype() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><export>&e;</export>";
        assertThrows(Exception.class, () -> SanctionsXmlParser.parse(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))));
    }
}
