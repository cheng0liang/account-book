package com.accountbook.ocr.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReceiptJsonParserTest {

    @Test
    void parsesFencedJsonWithStringNumbers() throws Exception {
        String raw = """
                ```json
                {"merchant":{"name":"西友 涩谷店","country":"jp","city":"东京"},
                 "purchasedAt":"2026-11-06 12:30","currency":"jpy","totalAmount":"¥1,280",
                 "items":[{"name":"明治牛奶","specText":"1000ml","quantity":2,"unitPrice":"198","amount":396},
                          {"name":null,"amount":1}]}
                ```""";
        var r = ReceiptJsonParser.parse(raw, "llm", "JPY");
        assertThat(r.merchantName()).isEqualTo("西友 涩谷店");
        assertThat(r.country()).isEqualTo("JP");
        assertThat(r.currency()).isEqualTo("JPY");
        assertThat(r.purchasedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 30));
        assertThat(r.totalAmount()).isEqualByComparingTo(new BigDecimal("1280"));
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).unitPrice()).isEqualByComparingTo("198");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void warnsOnMissingFields() throws Exception {
        var r = ReceiptJsonParser.parse("{\"purchasedAt\":\"2026-11-06\",\"items\":[]}", "llm", "CNY");
        assertThat(r.currency()).isEqualTo("CNY");
        assertThat(r.purchasedAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 12, 0));
        assertThat(r.warnings()).contains("merchant_missing", "time_missing", "no_items");
    }

    @Test
    void rejectsNonJson() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> ReceiptJsonParser.parse("sorry, cannot read", "llm", "JPY"));
    }
}
