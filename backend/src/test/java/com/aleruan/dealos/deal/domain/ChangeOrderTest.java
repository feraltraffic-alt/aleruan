package com.aleruan.dealos.deal.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeOrderTest {

    private static final BigDecimal DEAL_MARKUP = new BigDecimal("25");

    private static ChangeOrder draft() {
        return new ChangeOrder(UUID.randomUUID(), 1, "Q3 plaster", "Customer asks for Q3", 5, Instant.now(), "lena");
    }

    @Test
    void lifecycleFreezesPricesAfterSendingButKeepsCostsOpen() {
        ChangeOrder co = draft();
        assertThrows(IllegalStateException.class, () -> co.send(Instant.now())); // no lines
        ChangeOrderLine plaster = co.addLine(LineKind.WORK, "Q3 plaster", "FINISHING", "m2", new BigDecimal("100"),
                new BigDecimal("8"), null, null, null);
        // scope taken out: negative quantity reduces the price
        co.addLine(LineKind.WORK, "Q2 plaster not needed", "FINISHING", "m2", new BigDecimal("-100"),
                new BigDecimal("6"), null, null, null);
        assertEquals(new BigDecimal("250.00"), co.totals(DEAL_MARKUP).price()); // 100×10 − 100×7.50
        assertEquals(new BigDecimal("200.00"), co.totals(DEAL_MARKUP).cost());

        co.send(Instant.now());
        assertThrows(IllegalStateException.class, () -> co.addLine(LineKind.WORK, "x", null, null, BigDecimal.ONE,
                BigDecimal.ONE, null, null, null));
        assertThrows(IllegalStateException.class, () -> co.updateLine(plaster.getId(), LineKind.WORK, "Q3 plaster",
                "FINISHING", "m2", new BigDecimal("120"), new BigDecimal("8"), null, null, null, DEAL_MARKUP));
        // a cheaper crew: cost changes, the customer's price stays
        co.updateLine(plaster.getId(), LineKind.WORK, "Q3 plaster", "FINISHING", "m2", new BigDecimal("100"),
                new BigDecimal("7"), null, null, null, DEAL_MARKUP);
        assertEquals(new BigDecimal("250.00"), co.totals(DEAL_MARKUP).price());
        assertEquals(new BigDecimal("100.00"), co.totals(DEAL_MARKUP).cost());

        assertThrows(IllegalArgumentException.class, () -> co.approve(" ", null, Instant.now()));
        co.approve("Jan Novák, stavbyvedoucí", "OK by phone, signed copy follows", Instant.now());
        assertEquals(ChangeOrderStatus.APPROVED, co.getStatus());
        assertEquals("Jan Novák, stavbyvedoucí", co.getDecidedBy());
        assertThrows(IllegalStateException.class, co::revise);
        assertThrows(IllegalStateException.class, co::cancel);
    }

    @Test
    void sentOrderCanBeRevisedAndRejected() {
        ChangeOrder co = draft();
        co.addLine(LineKind.SERVICE, "Night shift surcharge", null, "h", new BigDecimal("10"), new BigDecimal("20"),
                null, null, null);
        co.send(Instant.now());
        co.revise();
        assertEquals(ChangeOrderStatus.DRAFT, co.getStatus());
        co.edit("Night shifts", null, 0);
        co.send(Instant.now());
        co.reject("Anna", "Too expensive", Instant.now());
        assertEquals(ChangeOrderStatus.REJECTED, co.getStatus());
        assertThrows(IllegalArgumentException.class, () -> draft().addLine(LineKind.WORK, "zero", null, null,
                BigDecimal.ZERO, BigDecimal.ONE, null, null, null));
        assertEquals("D-2026-0003-C1", draft().number("D-2026-0003"));
    }
}
