package com.accountbook.ocr.internal;

import com.accountbook.ocr.api.OcrItem;
import com.accountbook.ocr.api.OcrResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Deterministic sample receipt so the whole flow can be tried without any OCR backend. */
class MockOcrProvider implements OcrProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public OcrResult recognize(byte[] image, String contentType, String defaultCurrency) {
        String currency = defaultCurrency == null ? "JPY" : defaultCurrency;
        boolean jpy = "JPY".equals(currency);
        BigDecimal milk = jpy ? bd("198") : bd("12.8");
        BigDecimal bread = jpy ? bd("158") : bd("9.9");
        BigDecimal water = jpy ? bd("98") : bd("2.5");
        var items = List.of(
                new OcrItem("明治牛奶", "明治", "1000ml", bd("2"), milk, null, milk.multiply(bd("2")), "BEVERAGE"),
                new OcrItem("吐司面包", null, null, bd("1"), bread, null, bread, "FOOD"),
                new OcrItem("矿泉水", "Suntory", "500ml", bd("3"), water, null, water.multiply(bd("3")), "BEVERAGE"));
        BigDecimal total = items.stream().map(OcrItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OcrResult(name(), List.of("ocr_mock_data"), "示例超市 涩谷店", jpy ? "JP" : null, jpy ? "东京" : null,
                jpy ? "涩谷" : null, null, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), currency, total, null,
                null, "CASH", items);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
