package com.aleruan.dealos.counterparty.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistrDphResultMapperTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");

    private static String response(String payer) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <StatusNespolehlivyPlatceRozsirenyResponse xmlns="http://adis.mfcr.cz/rozhraniCRPDPH/">
                      <status odpovedGenerovana="2026-10-23" statusCode="0" statusText="OK"/>
                      %s
                    </StatusNespolehlivyPlatceRozsirenyResponse>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(payer);
    }

    @Test
    void reliablePayerWithAccounts() {
        RegistrySnapshot s = RegistrDphResultMapper.map(response("""
                <statusPlatceDPH dic="68599528" nespolehlivyPlatce="NE" cisloFu="13">
                  <zverejneneUcty>
                    <ucet datumZverejneni="2020-01-01"><standardniUcet predcisli="19" cislo="2000145399" kodBanky="0800"/></ucet>
                    <ucet datumZverejneni="2021-01-01"><nestandardniUcet cislo="DE89370400440532013000"/></ucet>
                  </zverejneneUcty>
                  <nazevSubjektu>Stavby Horák s.r.o.</nazevSubjektu>
                  <adresa><uliceCislo>Vinohradská 112</uliceCislo><mesto>PRAHA 3</mesto><psc>13000</psc></adresa>
                </statusPlatceDPH>"""), AT);
        assertTrue(s.available());
        assertEquals(Boolean.FALSE, s.unreliableVatPayer());
        assertEquals("CZ68599528", s.vatNumber());
        assertEquals("Stavby Horák s.r.o.", s.legalName());
        assertEquals("Vinohradská 112, PRAHA 3, 13000", s.address());
        assertEquals(List.of("19-2000145399/0800", "DE89370400440532013000"), s.publishedAccounts());
        assertTrue(SnapshotAnalyzer.findings(s, TODAY).isEmpty());
        assertTrue(SnapshotAnalyzer.accountFindings(s, "CZ65 0800 0000 1920 0014 5399").isEmpty());
        assertEquals(Set.of(CheckFinding.ACCOUNT_NOT_PUBLISHED), SnapshotAnalyzer.accountFindings(s, "123456789/0100"));
    }

    @Test
    void unreliablePayerIsRed() {
        RegistrySnapshot s = RegistrDphResultMapper.map(response(
                "<statusPlatceDPH dic=\"12345678\" nespolehlivyPlatce=\"ANO\"/>"), AT);
        assertEquals(Set.of(CheckFinding.UNRELIABLE_VAT_PAYER), SnapshotAnalyzer.findings(s, TODAY));
    }

    @Test
    void notAVatPayerIsYellow() {
        RegistrySnapshot s = RegistrDphResultMapper.map(response(
                "<statusPlatceDPH dic=\"12345678\" nespolehlivyPlatce=\"NENALEZEN\"/>"), AT);
        assertEquals(Set.of(CheckFinding.NOT_VAT_PAYER), SnapshotAnalyzer.findings(s, TODAY));
    }

    @Test
    void serviceErrorsAreUnavailable() {
        String error = response("").replace("statusCode=\"0\" statusText=\"OK\"", "statusCode=\"2\" statusText=\"Prekrocen limit\"");
        assertFalse(RegistrDphResultMapper.map(error, AT).available());
        assertFalse(RegistrDphResultMapper.map("not xml", AT).available());
        assertFalse(RegistrDphResultMapper.map("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
                <faultstring>boom</faultstring></s:Fault></s:Body></s:Envelope>""", AT).available());
    }

    @Test
    void refusesDoctypeAndBadDic() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertFalse(RegistrDphResultMapper.map(xxe, AT).available());
        assertThrows(IllegalArgumentException.class, () -> RegistrDphResultMapper.request("12<x/>"));
        assertTrue(RegistrDphResultMapper.request("68599528").contains("<roz:dic>68599528</roz:dic>"));
    }
}
