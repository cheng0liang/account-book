package com.accountbook.ocr.internal;

import com.accountbook.common.i18n.MessageBundle;
import com.accountbook.ocr.api.OcrApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OcrProperties.class)
class OcrModuleConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrModuleConfig.class);

    @Bean
    MessageBundle ocrMessages() {
        return new MessageBundle("i18n/ocr");
    }

    @Bean
    OcrApi ocrApi(OcrProperties props) {
        return new OcrService(select(props));
    }

    static OcrProvider select(OcrProperties props) {
        String mode = props.provider() == null ? "auto" : props.provider().strip().toLowerCase();
        OcrProvider provider = switch (mode) {
            case "llm" -> new LlmVisionOcrProvider(props.llm());
            case "mock" -> new MockOcrProvider();
            case "noop" -> new NoopOcrProvider();
            default -> props.llm().configured() ? new LlmVisionOcrProvider(props.llm()) : new MockOcrProvider();
        };
        log.info("OCR provider: {} (ab.ocr.provider={})", provider.name(), mode);
        return provider;
    }
}
