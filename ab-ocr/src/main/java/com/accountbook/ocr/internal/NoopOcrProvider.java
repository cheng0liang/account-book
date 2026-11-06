package com.accountbook.ocr.internal;

import com.accountbook.ocr.api.OcrResult;
import java.util.List;

/** Returns nothing: the user fills the form by hand. */
class NoopOcrProvider implements OcrProvider {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public OcrResult recognize(byte[] image, String contentType, String defaultCurrency) {
        return OcrResult.empty(name(), List.of("ocr_disabled"));
    }
}
