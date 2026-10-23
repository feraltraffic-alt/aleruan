package com.aleruan.dealos.docimport.reader;

import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Claude reads documents only when an API key is configured; without one, documents wait and can be typed in. */
@Configuration
class ReaderConfig {

    @Bean
    @ConditionalOnExpression("!'${dealos.import.anthropic-api-key:}'.isBlank()")
    DocumentReader claudeDocumentReader(@Value("${dealos.import.anthropic-api-key}") String key,
                                        @Value("${dealos.import.model:claude-sonnet-5-5}") String model,
                                        @Value("${dealos.import.api-url:https://api.anthropic.com}") String url,
                                        @Value("${dealos.import.max-tokens:16000}") int maxTokens,
                                        @Value("${dealos.import.timeout:PT3M}") Duration timeout,
                                        CompanyProfile company, ObjectMapper json) {
        LoggerFactory.getLogger(ReaderConfig.class).info("Document import reads with {}", model);
        return new ClaudeDocumentReader(key.strip(), model, url, maxTokens, timeout, Duration.ofSeconds(5), company, json);
    }
}
