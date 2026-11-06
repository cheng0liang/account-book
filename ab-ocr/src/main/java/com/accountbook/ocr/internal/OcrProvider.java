package com.accountbook.ocr.internal;

import com.accountbook.ocr.api.OcrResult;

/** SPI: one implementation per recognition backend. */
interface OcrProvider {

    String name();

    OcrResult recognize(byte[] image, String contentType, String defaultCurrency) throws Exception;
}
