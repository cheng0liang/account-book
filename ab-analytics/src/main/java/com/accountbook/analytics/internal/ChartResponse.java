package com.accountbook.analytics.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/** Columnar chart payload: {@code data} of every series is aligned with {@code labels}; gaps are null. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ChartResponse(String currency, String unit, List<String> labels, List<Series> series, List<Long> counts,
                     long version, List<MetaItem> meta, BigDecimal total, Long count) {

    record Series(String key, String name, List<BigDecimal> data) {
    }

    record MetaItem(Object id, String name) {
    }

    static ChartResponse empty(String currency, String unit, long version) {
        return new ChartResponse(currency, unit, List.of(), List.of(), List.of(), version, null, null, null);
    }
}
