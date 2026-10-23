package com.aleruan.dealos.invoice.domain;

import com.aleruan.dealos.platform.documents.DocLanguage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Invoice labels. Czech always comes first (the document is a Czech tax document); for foreign
 * customers the label in their language follows after a slash.
 */
public final class InvoiceTexts {

    public enum Key {
        TITLE, CREDIT_TITLE, NUMBER, ISSUED, TAXABLE, DUE, SYMBOL, SUPPLIER, CUSTOMER, COMPANY_ID, VAT_ID, DEAL,
        CREDIT_OF, DESCRIPTION, QUANTITY, UNIT, UNIT_PRICE, LINE_TOTAL, SUBTOTAL, VAT, NO_VAT, TOTAL, BANK, RATE,
        IN_CZK, PAY_QR, ISSUED_BY, NOTE, PAGE
    }

    private static final Map<DocLanguage, Map<Key, String>> LABELS = new EnumMap<>(DocLanguage.class);

    static {
        put(DocLanguage.CS, "Faktura – daňový doklad", "Opravný daňový doklad", "Číslo", "Datum vystavení",
                "Datum zdanitelného plnění", "Datum splatnosti", "Variabilní symbol", "Dodavatel", "Odběratel", "IČO",
                "DIČ", "Zakázka", "Opravuje doklad", "Popis", "Množství", "MJ", "Cena za MJ", "Celkem",
                "Základ daně", "DPH {rate} %", "DPH", "Celkem k úhradě", "Bankovní spojení",
                "Kurz ČNB ze dne {date}: 1 {cur} = {rate} CZK", "Částky v CZK", "Zaplatit QR kódem", "Vystavil", "Poznámka", "Strana");
        put(DocLanguage.EN, "Invoice", "Credit note", "Number", "Issue date", "Date of supply", "Due date",
                "Payment reference", "Supplier", "Customer", "Reg. No.", "VAT ID", "Deal", "Corrects invoice",
                "Description", "Qty", "Unit", "Unit price", "Total", "Tax base", "VAT {rate} %", "VAT", "Total due",
                "Bank details", "ČNB rate of {date}: 1 {cur} = {rate} CZK", "Amounts in CZK", "Pay by QR", "Issued by",
                "Note", "Page");
        put(DocLanguage.PL, "Faktura", "Faktura korygująca", "Numer", "Data wystawienia", "Data dostawy",
                "Termin płatności", "Tytuł przelewu", "Sprzedawca", "Nabywca", "Nr rejestrowy", "NIP UE", "Zlecenie",
                "Koryguje fakturę", "Opis", "Ilość", "J.m.", "Cena jedn.", "Wartość", "Netto", "VAT {rate} %", "VAT",
                "Do zapłaty", "Dane do przelewu", "Kurs ČNB z dnia {date}: 1 {cur} = {rate} CZK", "Kwoty w CZK",
                "Zapłać kodem QR", "Wystawił", "Uwagi", "Strona");
        put(DocLanguage.DE, "Rechnung", "Gutschrift (Rechnungskorrektur)", "Nummer", "Rechnungsdatum", "Leistungsdatum",
                "Fällig am", "Verwendungszweck", "Lieferant", "Kunde", "Reg.-Nr.", "USt-IdNr.", "Auftrag", "Korrektur zu",
                "Beschreibung", "Menge", "Einheit", "Einzelpreis", "Gesamt", "Netto", "USt. {rate} %", "USt.",
                "Zu zahlen", "Bankverbindung", "ČNB-Kurs vom {date}: 1 {cur} = {rate} CZK", "Beträge in CZK",
                "Mit QR bezahlen", "Erstellt von", "Hinweis", "Seite");
    }

    private static void put(DocLanguage lang, String... values) {
        Key[] keys = Key.values();
        if (values.length != keys.length) {
            throw new IllegalStateException("Invoice labels for " + lang + ": expected " + keys.length + ", got " + values.length);
        }
        Map<Key, String> m = new EnumMap<>(Key.class);
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], values[i]);
        }
        LABELS.put(lang, m);
    }

    private InvoiceTexts() {
    }

    public static String one(DocLanguage lang, Key key) {
        return LABELS.get(lang).get(key);
    }

    /** "Datum splatnosti / Due date", or just the Czech label for Czech documents. */
    public static String both(DocLanguage lang, Key key) {
        String cs = one(DocLanguage.CS, key);
        return lang == DocLanguage.CS ? cs : cs + " / " + one(lang, key);
    }

    public static String companyIdLabel(DocLanguage lang, String country) {
        if ("CZ".equalsIgnoreCase(country) || "SK".equalsIgnoreCase(country)) {
            return "IČO";
        }
        if ("PL".equalsIgnoreCase(country)) {
            return "NIP";
        }
        return one(lang, Key.COMPANY_ID);
    }
}
