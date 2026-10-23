package com.aleruan.dealos.counterparty.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Shared HTTP plumbing for registry clients: timeouts and JSON parsing into plain maps. */
@Component
class RegistryHttp {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final RegistryProperties properties;

    RegistryHttp(RestClient.Builder builder, ObjectMapper objectMapper, RegistryProperties properties) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    Map<String, Object> parse(String body) {
        try {
            return objectMapper.readValue(body, MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Registry returned non-JSON body", e);
        }
    }

    RegistryProperties properties() {
        return properties;
    }
}
