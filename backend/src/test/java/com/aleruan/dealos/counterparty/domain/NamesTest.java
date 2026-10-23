package com.aleruan.dealos.counterparty.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void dropsLegalFormsPunctuationAndDiacritics() {
        assertEquals("ALERUAN INDUSTRY", Names.forMatching("Aleruan-industry s.r.o."));
        assertEquals("ALERUAN INDUSTRY", Names.forMatching("ALERUAN-INDUSTRY S.R.O."));
        assertEquals("POLUDNIE BUDOWA", Names.forMatching("Południe Budowa Sp. z o.o."));
        assertEquals("MONTAGE RHEIN", Names.forMatching("Montage Rhein GmbH"));
    }

    @Test
    void keepsCyrillicLettersAndDropsCyrillicLegalForms() {
        assertEquals(Names.forMatching("Стройинвест"), Names.forMatching("ООО «Стройинвест»"));
        assertEquals(Names.forMatching("СТРОЙИНВЕСТ"), Names.forMatching("Стройинвест"));
        assertEquals(1, Names.forMatching("ООО «Стройинвест»").split(" ").length);
    }
}
