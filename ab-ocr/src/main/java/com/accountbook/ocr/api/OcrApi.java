package com.accountbook.ocr.api;

public interface OcrApi {

    /** Name of the provider currently in use: {@code llm}, {@code mock} or {@code noop}. */
    String providerName();

    /**
     * @param defaultCurrency hint used when the receipt does not show a currency
     * @throws com.accountbook.common.web.ApiException 502 {@code OCR_FAILED} when the provider fails
     */
    OcrResult recognize(byte[] image, String contentType, String defaultCurrency);
}
