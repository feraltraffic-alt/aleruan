package com.aleruan.dealos.docimport.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a local stand-in for the Messages API: request shape, answer parsing, retries and errors. */
class ClaudeDocumentReaderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String EXTRACTION = """
            {"documentType":"SUPPLIER_INVOICE","summary":"Invoice for plastering","documentNumber":"2026-0117",
             "issueDate":"2026-10-01","taxableDate":"2026-10-01","dueDate":"2026-10-15","currency":"CZK",
             "variableSymbol":"20260117",
             "issuer":{"name":"Stavby Horák s.r.o.","companyId":"68599528","vatNumber":"CZ68599528","address":"Praha",
                       "country":"CZ","bankAccount":"123456789/0100"},
             "recipient":{"name":"Aleruan-industry s.r.o.","companyId":"14220997","vatNumber":"CZ14220997",
                          "address":"Praha 3","country":"CZ","bankAccount":""},
             "netAmount":"10000.00","vatAmount":"2100.00","totalAmount":"12100.00","vatNote":"","references":"D-2026-0004",
             "lines":[{"description":"Omítky","quantity":"100","unit":"m2","unitPrice":"100.00","total":"10000.00","vatRate":"21"}],
             "warnings":[]}""";

    private HttpServer server;
    private final Deque<int[]> statuses = new ConcurrentLinkedDeque<>();
    private final Deque<String> bodies = new ConcurrentLinkedDeque<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<String> versions = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(ex.getRequestHeaders().getFirst("x-api-key"));
            versions.add(ex.getRequestHeaders().getFirst("anthropic-version"));
            int[] status = statuses.isEmpty() ? new int[] {200} : statuses.poll();
            byte[] out = (bodies.isEmpty() ? answer("end_turn") : bodies.poll()).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status[0], out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ClaudeDocumentReader reader() {
        CompanyProfile company = new CompanyProfile("Aleruan-industry s.r.o.", "CZ", "14220997", "CZ14220997",
                "Malešická 2855/2b, Praha 3", null, null, null, null, null, null);
        return new ClaudeDocumentReader("sk-test", "claude-sonnet-5-5", "http://127.0.0.1:" + server.getAddress().getPort(),
                4000, Duration.ofSeconds(10), Duration.ofMillis(1), company, JSON);
    }

    private static String answer(String stopReason) {
        try {
            return JSON.writeValueAsString(JSON.createObjectNode()
                    .put("model", "claude-sonnet-5-5").put("stop_reason", stopReason)
                    .<com.fasterxml.jackson.databind.node.ObjectNode>set("content", JSON.createArrayNode()
                            .add(JSON.createObjectNode().put("type", "text").put("text", EXTRACTION)))
                    .set("usage", JSON.createObjectNode().put("input_tokens", 1834).put("output_tokens", 412)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sendsTheDocumentWithTheSchemaAndReadsTheAnswer() throws Exception {
        DocumentReader.Result r = reader().read(PDF, "application/pdf", "faktura.pdf");
        assertEquals("SUPPLIER_INVOICE", r.extraction().documentType());
        assertEquals("68599528", r.extraction().issuer().companyId());
        assertEquals("10000.00", r.extraction().lines().get(0).total());
        assertEquals(1834, r.inputTokens());
        assertEquals(412, r.outputTokens());

        assertEquals("sk-test", keys.get(0));
        assertEquals("2023-06-01", versions.get(0));
        JsonNode req = JSON.readTree(requests.get(0));
        assertEquals("claude-sonnet-5-5", req.path("model").asText());
        assertTrue(req.path("system").asText().contains("Aleruan-industry s.r.o. (IČO 14220997, DIČ CZ14220997)"));
        JsonNode doc = req.path("messages").get(0).path("content").get(0);
        assertEquals("document", doc.path("type").asText());
        assertEquals("application/pdf", doc.path("source").path("media_type").asText());
        assertEquals(Base64.getEncoder().encodeToString(PDF), doc.path("source").path("data").asText());
        JsonNode format = req.path("output_config").path("format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals(false, format.path("schema").path("additionalProperties").asBoolean(true));
    }

    @Test
    void photosGoAsImages() throws Exception {
        reader().read(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg", "scan.jpg");
        JsonNode block = JSON.readTree(requests.get(0)).path("messages").get(0).path("content").get(0);
        assertEquals("image", block.path("type").asText());
        assertEquals("image/jpeg", block.path("source").path("media_type").asText());
    }

    @Test
    void retriesWhenBusyThenGivesUpWithAPlainMessage() {
        statuses.add(new int[] {529});
        bodies.add("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        assertEquals("SUPPLIER_INVOICE", reader().read(PDF, "application/pdf", "a.pdf").extraction().documentType());
        assertEquals(2, requests.size());

        for (int i = 0; i < 3; i++) {
            statuses.add(new int[] {429});
            bodies.add("{\"type\":\"error\",\"error\":{\"message\":\"rate limited\"}}");
        }
        DocumentReader.ReadFailed busy = assertThrows(DocumentReader.ReadFailed.class,
                () -> reader().read(PDF, "application/pdf", "a.pdf"));
        assertTrue(busy.getMessage().contains("busy"));
    }

    @Test
    void wrongKeyRefusalAndTruncationAreExplained() {
        statuses.add(new int[] {401});
        bodies.add("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        assertTrue(assertThrows(DocumentReader.ReadFailed.class, () -> reader().read(PDF, "application/pdf", "a.pdf"))
                .getMessage().contains("ANTHROPIC_API_KEY"));

        bodies.add(answer("refusal"));
        assertTrue(assertThrows(DocumentReader.ReadFailed.class, () -> reader().read(PDF, "application/pdf", "a.pdf"))
                .getMessage().contains("declined"));

        bodies.add(answer("max_tokens"));
        assertTrue(assertThrows(DocumentReader.ReadFailed.class, () -> reader().read(PDF, "application/pdf", "a.pdf"))
                .getMessage().contains("too long"));

        assertThrows(DocumentReader.ReadFailed.class, () -> reader().read(PDF, "text/html", "a.html"));
    }
}
