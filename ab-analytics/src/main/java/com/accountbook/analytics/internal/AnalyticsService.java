package com.accountbook.analytics.internal;

import com.accountbook.common.money.Money;
import com.accountbook.common.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongFunction;
import org.springframework.stereotype.Service;

/**
 * Chart queries behind a two-level read path: (1) the data version (bumped inside every projection write) is the ETag,
 * so unchanged data costs one PK lookup and a 304; (2) serialized JSON is cached per (version, query), so a changed
 * dataset is computed at most once per distinct query. Old versions simply age out of the LRU.
 */
@Service
class AnalyticsService {

    /** Serialized response plus the validator it was produced for. */
    record Payload(String etag, byte[] body) {
    }

    private final QueryRepository queries;
    private final ProjectionRepository projection;
    private final ObjectMapper json;
    private final Cache<String, byte[]> cache = Caffeine.newBuilder().maximumSize(512).build();

    AnalyticsService(QueryRepository queries, ProjectionRepository projection, ObjectMapper json) {
        this.queries = queries;
        this.projection = projection;
        this.json = json;
    }

    /** Returns null when {@code ifNoneMatch} still matches (caller answers 304) - computed only on a miss. */
    Payload serve(String endpoint, Object key, String ifNoneMatch, LongFunction<Object> compute) {
        long version = projection.version();
        String cacheKey = version + "|" + endpoint + "|" + key;
        String etag = "\"v" + version + "-" + Integer.toHexString((endpoint + key).hashCode()) + "\"";
        if (ifNoneMatch != null && matches(ifNoneMatch, etag)) {
            return new Payload(etag, null);
        }
        byte[] body = cache.get(cacheKey, k -> write(compute.apply(version)));
        return new Payload(etag, body);
    }

    private static boolean matches(String header, String etag) {
        for (String part : header.split(",")) {
            String p = part.strip();
            if (p.startsWith("W/")) {
                p = p.substring(2);
            }
            if (p.equals(etag) || p.equals("*")) {
                return true;
            }
        }
        return false;
    }

    private byte[] write(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- charts -----------------------------------------------------------------------------------------------

    ChartResponse priceTrend(Filter f, List<Long> merchantIds, String granularity, String metric, boolean normalized,
                             long version) {
        var c = queries.context(f, normalized);
        if (c.empty()) {
            return ChartResponse.empty(f.currency(), c.unit(), version);
        }
        List<Long> mids = merchantIds.isEmpty() ? queries.topMerchants(c, 6) : merchantIds;
        if (mids.isEmpty()) {
            return ChartResponse.empty(f.currency(), c.unit(), version);
        }
        var rows = queries.trend(c, mids, granularity);
        TreeMap<String, Integer> labelIndex = new TreeMap<>();
        rows.forEach(r -> labelIndex.put(r.bucket(), 0));
        List<String> labels = new ArrayList<>(labelIndex.keySet());
        for (int i = 0; i < labels.size(); i++) {
            labelIndex.put(labels.get(i), i);
        }
        long[] counts = new long[labels.size()];
        Map<Long, List<BigDecimal>> data = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (Long id : mids) {
            data.put(id, new ArrayList<>(java.util.Collections.nCopies(labels.size(), null)));
        }
        for (var r : rows) {
            int idx = labelIndex.get(r.bucket());
            double v = switch (metric) {
                case "min" -> r.min();
                case "max" -> r.max();
                default -> r.avg();
            };
            data.get(r.merchantId()).set(idx, Money.fromMinorAvg(v, f.currency()));
            names.putIfAbsent(r.merchantId(), r.merchantName());
            counts[idx] += r.n();
        }
        List<ChartResponse.Series> series = new ArrayList<>();
        data.forEach((id, values) -> {
            if (names.containsKey(id)) {
                series.add(new ChartResponse.Series("m:" + id, names.get(id), values));
            }
        });
        return new ChartResponse(f.currency(), c.unit(), labels, series, boxed(counts), version, null, null, null);
    }

    ChartResponse priceCompare(Filter f, String dimension, boolean normalized, long version) {
        var c = queries.context(f, normalized);
        if (c.empty()) {
            return ChartResponse.empty(f.currency(), c.unit(), version);
        }
        var rows = queries.compare(c, dimension);
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<BigDecimal> avg = new ArrayList<>();
        List<BigDecimal> min = new ArrayList<>();
        List<BigDecimal> max = new ArrayList<>();
        List<ChartResponse.MetaItem> meta = new ArrayList<>();
        for (var r : rows) {
            labels.add(r.label());
            counts.add(r.n());
            avg.add(Money.fromMinorAvg(r.avg(), f.currency()));
            min.add(Money.fromMinorAvg(r.min(), f.currency()));
            max.add(Money.fromMinorAvg(r.max(), f.currency()));
            meta.add(new ChartResponse.MetaItem(r.id(), r.label()));
        }
        var series = List.of(new ChartResponse.Series("avg", "avg", avg), new ChartResponse.Series("min", "min", min),
                new ChartResponse.Series("max", "max", max));
        return new ChartResponse(f.currency(), c.unit(), labels, series, counts, version, meta, null, null);
    }

    ChartResponse spending(Filter f, String groupBy, int topN, long version) {
        var c = queries.context(f, false);
        var rows = queries.spending(c, groupBy);
        long total = rows.stream().mapToLong(QueryRepository.SpendRow::amountMinor).sum();
        boolean time = groupBy.equals("day") || groupBy.equals("month");
        var shown = time || rows.size() <= topN ? rows : rows.subList(0, topN);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<ChartResponse.MetaItem> meta = new ArrayList<>();
        for (var r : shown) {
            labels.add(r.label());
            amounts.add(Money.fromMinor(r.amountMinor(), f.currency()));
            counts.add(r.n());
            meta.add(new ChartResponse.MetaItem(groupBy.equals("merchant") ? Long.valueOf(r.id()) : r.id(), r.label()));
        }
        return new ChartResponse(f.currency(), "item", labels,
                List.of(new ChartResponse.Series("amount", "amount", amounts)), counts, version, meta,
                Money.fromMinor(total, f.currency()), queries.receiptCount(c));
    }

    Map<String, Object> priceTable(Filter f, boolean normalized, long version) {
        var c = queries.context(f, normalized);
        List<Map<String, Object>> out = new ArrayList<>();
        if (!c.empty()) {
            Map<Long, QueryRepository.Latest> latest = new LinkedHashMap<>();
            queries.latest(c).forEach(l -> latest.put(l.merchantId(), l));
            for (var r : queries.priceTable(c)) {
                var l = latest.get(r.merchantId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("merchantId", r.merchantId());
                row.put("merchantName", r.name());
                row.put("city", r.city());
                row.put("district", r.district());
                row.put("latestPrice", Money.fromMinorAvg(l.price(), f.currency()));
                row.put("latestDate", l.date());
                row.put("minPrice", Money.fromMinorAvg(r.min(), f.currency()));
                row.put("avgPrice", Money.fromMinorAvg(r.avg(), f.currency()));
                row.put("maxPrice", Money.fromMinorAvg(r.max(), f.currency()));
                row.put("count", r.n());
                out.add(row);
            }
            out.sort(Comparator.comparing(m -> (BigDecimal) m.get("latestPrice")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currency", f.currency());
        body.put("unit", c.unit());
        body.put("rows", out);
        body.put("version", version);
        return body;
    }

    Map<String, Object> facets(Collection<Long> userIds, long version) {
        Map<String, Object> body = queries.facets(userIds);
        body.put("version", version);
        return body;
    }

    static void requireScope(String dimension, Filter f) {
        boolean ok = switch (dimension) {
            case "merchant" -> f.hasProductScope();
            case "product" -> f.merchantId() != null || f.hasProductScope();
            default -> true;
        };
        if (!ok) {
            throw ApiException.badRequest("analytics.filter.required");
        }
    }

    private static List<Long> boxed(long[] a) {
        List<Long> out = new ArrayList<>(a.length);
        for (long v : a) {
            out.add(v);
        }
        return out;
    }
}
