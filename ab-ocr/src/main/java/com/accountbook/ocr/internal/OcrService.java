package com.accountbook.ocr.internal;

import com.accountbook.common.web.ApiException;
import com.accountbook.ocr.api.OcrApi;
import com.accountbook.ocr.api.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class OcrService implements OcrApi {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrProvider provider;

    OcrService(OcrProvider provider) {
        this.provider = provider;
    }

    @Override
    public String providerName() {
        return provider.name();
    }

    @Override
    public OcrResult recognize(byte[] image, String contentType, String defaultCurrency) {
        if (image == null || image.length == 0) {
            throw ApiException.invalidField("image", "ocr.emptyImage");
        }
        try {
            return provider.recognize(image, contentType, defaultCurrency);
        } catch (Exception e) {
            log.warn("OCR provider {} failed: {}", provider.name(), e.toString());
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "OCR_FAILED", "ocr.failed");
        }
    }
}
