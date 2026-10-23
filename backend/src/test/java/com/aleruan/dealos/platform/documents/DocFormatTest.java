package com.aleruan.dealos.platform.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DocFormatTest {

    @Test
    void escapesMarkupAndDropsCharactersXmlCannotCarry() {
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot; &#39;d&#39;", DocFormat.esc("a <b> & \"c\" 'd'"));
        // a lone surrogate (broken copy-paste), U+FFFE and a bell character disappear; real emoji stay
        assertEquals("ok 👷 ok", DocFormat.esc("ok\uD83D 👷￾\u0007 ok"));
        assertEquals("Příčky\tQ2\nřádek", DocFormat.esc("Příčky\tQ2\nřádek"));
        assertEquals("", DocFormat.esc(null));
    }
}
