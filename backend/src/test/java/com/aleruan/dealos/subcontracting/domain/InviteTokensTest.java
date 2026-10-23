package com.aleruan.dealos.subcontracting.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InviteTokensTest {

    @Test
    void tokensAreLongRandomAndHashedDeterministically() {
        String t1 = InviteTokens.newToken();
        String t2 = InviteTokens.newToken();
        assertEquals(43, t1.length()); // 256 bits, base64url without padding
        assertNotEquals(t1, t2);
        assertEquals(InviteTokens.hash(t1), InviteTokens.hash(t1));
        assertEquals(64, InviteTokens.hash(t1).length());
    }

    @Test
    void malformedTokensAreRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> InviteTokens.hash("short"));
        assertThrows(IllegalArgumentException.class, () -> InviteTokens.hash("../../etc/passwd/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }
}
