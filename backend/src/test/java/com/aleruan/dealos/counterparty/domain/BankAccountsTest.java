package com.aleruan.dealos.counterparty.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BankAccountsTest {

    @Test
    void czechIbanAndDomesticFormsMatch() {
        String domestic = BankAccounts.comparable("19-2000145399/0800");
        assertEquals("19-2000145399/0800", domestic);
        assertEquals(domestic, BankAccounts.comparable("CZ65 0800 0000 1920 0014 5399"));
        assertEquals(domestic, BankAccounts.comparable("000019-2000145399/0800"));
    }

    @Test
    void czechAccountWithoutPrefix() {
        assertEquals("2000145399/0800", BankAccounts.comparable("CZ69 0800 0000 0020 0014 5399"));
        assertEquals("2000145399/0800", BankAccounts.comparable("0-2000145399/0800"));
    }

    @Test
    void polishIbanAndNrbMatch() {
        String nrb = "23102010130000020204812139"; // Aleruan's PKO BP account from the invoice
        assertEquals(nrb, BankAccounts.comparable("PL23 1020 1013 0000 0202 0481 2139"));
        assertEquals(nrb, BankAccounts.comparable("23 1020 1013 0000 0202 0481 2139"));
        assertEquals(nrb, BankAccounts.polishNrb("PL23 1020 1013 0000 0202 0481 2139"));
        assertNull(BankAccounts.polishNrb("CZ65 0800 0000 1920 0014 5399"));
    }
}
