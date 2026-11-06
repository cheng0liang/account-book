package com.accountbook.ocr.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code provider}: auto (llm when an api-key is set, otherwise mock) | llm | mock | noop. */
@ConfigurationProperties(prefix = "ab.ocr")
public record OcrProperties(@DefaultValue("auto") String provider, @DefaultValue Llm llm) {

    public record Llm(
            @DefaultValue("https://api.openai.com/v1") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("gpt-4o-mini") String model,
            @DefaultValue("60") int timeoutSeconds,
            @DefaultValue("2000") int maxTokens) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
