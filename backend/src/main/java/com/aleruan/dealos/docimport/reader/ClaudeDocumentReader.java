package com.aleruan.dealos.docimport.reader;

import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads documents with Claude through the Anthropic Messages API: the PDF or photo goes in as a document
 * or image block, and the answer is constrained to the extraction JSON schema (structured outputs).
 * The API key comes from the server's environment and never reaches the browser.
 */
public class ClaudeDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(ClaudeDocumentReader.class);
    static final String VERSION = "2023-06-01";
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final int MAX_TRIES = 3;

    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final int maxTokens;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Duration timeout;
    private final Duration backoff;
    private final String systemPrompt;
    private final JsonNode schema;

    public ClaudeDocumentReader(String apiKey, String model, String baseUrl, int maxTokens, Duration timeout,
                                Duration backoff, CompanyProfile company, ObjectMapper json) {
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/messages");
        this.maxTokens = maxTokens;
        this.json = json;
        this.timeout = timeout;
        this.backoff = backoff;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String ids = Stream.of(company.companyId() == null ? null : "IČO " + company.companyId(),
                        company.vatNumber() == null ? null : "DIČ " + company.vatNumber())
                .filter(s -> s != null).collect(Collectors.joining(", "));
        this.systemPrompt = resource("import/system-prompt.txt").replace("{company}", company.name())
                .replace("{companyIds}", ids);
        try {
            this.schema = json.readTree(resource("import/extraction-schema.json"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The extraction schema is not valid JSON", e);
        }
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public Result read(byte[] data, String contentType, String fileName) {
        String body = requestBody(data, contentType);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            java.util.concurrent.CompletableFuture<HttpResponse<String>> call =
                    http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            try {
                // the whole answer within the timeout, not only its headers
                response = call.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                call.cancel(true);
                if (attempt < MAX_TRIES) {
                    pause(attempt);
                    continue;
                }
                String why = e instanceof java.util.concurrent.TimeoutException ? "did not answer in time"
                        : "could not be reached: " + e.getCause();
                throw new ReadFailed("Claude " + why, true, e);
            } catch (InterruptedException e) {
                call.cancel(true);
                Thread.currentThread().interrupt();
                throw new ReadFailed("Reading was interrupted", true, e);
            }
            int status = response.statusCode();
            if (status == 200) {
                return parse(response.body());
            }
            boolean busy = status == 429 || status == 529 || status >= 500;
            if (busy && attempt < MAX_TRIES) {
                log.info("Claude answered {} for {}, retrying", status, fileName);
                pause(attempt);
                continue;
            }
            throw new ReadFailed(failure(status, response.body()), busy, null);
        }
    }

    String requestBody(byte[] data, String contentType) {
        ObjectNode root = json.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("system", systemPrompt);
        ObjectNode block = json.createObjectNode();
        ObjectNode source = block.putObject("source");
        source.put("type", "base64");
        if ("application/pdf".equals(contentType)) {
            block.put("type", "document");
            source.put("media_type", "application/pdf");
        } else if (IMAGES.contains(contentType)) {
            block.put("type", "image");
            source.put("media_type", contentType);
        } else {
            throw new ReadFailed("Only PDF files and photos can be read, not " + contentType);
        }
        source.put("data", Base64.getEncoder().encodeToString(data));
        ArrayNode content = json.createArrayNode();
        content.add(block);
        content.addObject().put("type", "text").put("text",
                "Read this document and return its data in the required JSON format.");
        root.putArray("messages").addObject().put("role", "user").set("content", content);
        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", schema);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    Result parse(String body) {
        try {
            JsonNode root = json.readTree(body);
            String stop = root.path("stop_reason").asText("");
            if ("refusal".equals(stop)) {
                throw new ReadFailed("Claude declined to read this document");
            }
            if ("max_tokens".equals(stop)) {
                throw new ReadFailed("The document is too long to read in one go; split it into smaller files");
            }
            String text = null;
            for (JsonNode c : root.path("content")) {
                if ("text".equals(c.path("type").asText())) {
                    text = c.path("text").asText();
                    break;
                }
            }
            if (text == null || text.isBlank()) {
                throw new ReadFailed("Claude returned no data for this document");
            }
            Extraction extraction = json.readValue(text, Extraction.class);
            JsonNode usage = root.path("usage");
            return new Result(extraction, root.path("model").asText(model),
                    usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                    usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null);
        } catch (JsonProcessingException e) {
            throw new ReadFailed("Claude's answer could not be understood", true, e);
        }
    }

    private String failure(int status, String body) {
        String message = "";
        try {
            message = json.readTree(body).path("error").path("message").asText("");
        } catch (JsonProcessingException ignored) {
            // not JSON
        }
        return switch (status) {
            case 401, 403 -> "The Anthropic API key is not valid or has no access (ANTHROPIC_API_KEY)";
            case 413 -> "The document is too large for Claude (32 MB per request)";
            case 429, 529 -> "Claude is busy right now; the document will be read again later";
            default -> "Claude answered " + status + (message.isBlank() ? "" : ": " + message);
        };
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(backoff.multipliedBy(attempt).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String resource(String path) {
        try (InputStream in = ClaudeDocumentReader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
