package com.accountbook.analytics.internal;

import com.accountbook.common.config.AppProperties;
import com.accountbook.common.web.ApiException;
import com.accountbook.family.api.FamilyApi;
import com.accountbook.family.api.Scope;
import com.accountbook.user.api.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {

    private final AnalyticsService service;
    private final ProjectionService projection;
    private final AppProperties props;
    private final FamilyApi families;

    AnalyticsController(AnalyticsService service, ProjectionService projection, AppProperties props,
                        FamilyApi families) {
        this.service = service;
        this.projection = projection;
        this.props = props;
        this.families = families;
    }

    /** The users whose data this request may read: the caller, or the caller's whole family for scope=family. */
    private List<Long> scopeOf(Map<String, String> p) {
        return Scope.parse(p.get("scope")).userIds(families, CurrentUser.id());
    }

    @GetMapping("/facets")
    ResponseEntity<byte[]> facets(@RequestParam Map<String, String> p,
                                  @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        List<Long> uids = scopeOf(p).stream().sorted().toList();
        return respond(service.serve("facets", uids, inm, v -> service.facets(uids, v)));
    }

    @GetMapping("/price-trend")
    ResponseEntity<byte[]> priceTrend(@RequestParam Map<String, String> p,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        Filter f = Filter.parse(p, props.defaultCurrency(), scopeOf(p));
        if (f.productId() == null) {
            throw ApiException.invalidField("productId", "validation.required");
        }
        var merchantIds = Filter.numbers(p, "merchantIds", 12);
        String granularity = Filter.choice(p, "granularity", Filter.GRANULARITY, "month");
        String metric = Filter.choice(p, "metric", Filter.METRIC, "avg");
        boolean normalized = Boolean.parseBoolean(p.get("normalized"));
        var key = f + "|" + merchantIds + "|" + granularity + "|" + metric + "|" + normalized;
        return respond(service.serve("price-trend", key, inm,
                v -> service.priceTrend(f, merchantIds, granularity, metric, normalized, v)));
    }

    @GetMapping("/price-compare")
    ResponseEntity<byte[]> priceCompare(@RequestParam Map<String, String> p,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        Filter f = Filter.parse(p, props.defaultCurrency(), scopeOf(p));
        String dimension = Filter.choice(p, "dimension", Filter.COMPARE, "merchant");
        AnalyticsService.requireScope(dimension, f);
        boolean normalized = Boolean.parseBoolean(p.get("normalized"));
        var key = f + "|" + dimension + "|" + normalized;
        return respond(service.serve("price-compare", key, inm,
                v -> service.priceCompare(f, dimension, normalized, v)));
    }

    @GetMapping("/spending")
    ResponseEntity<byte[]> spending(@RequestParam Map<String, String> p,
                                    @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        Filter f = Filter.parse(p, props.defaultCurrency(), scopeOf(p));
        String groupBy = Filter.choice(p, "groupBy", Filter.SPENDING, "month");
        Long n = Filter.number(p, "topN");
        int topN = n == null ? 10 : (int) Math.min(Math.max(n, 1), 50);
        var key = f + "|" + groupBy + "|" + topN;
        return respond(service.serve("spending", key, inm, v -> service.spending(f, groupBy, topN, v)));
    }

    @GetMapping("/price-table")
    ResponseEntity<byte[]> priceTable(@RequestParam Map<String, String> p,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String inm) {
        Filter f = Filter.parse(p, props.defaultCurrency(), scopeOf(p));
        if (f.productId() == null) {
            throw ApiException.invalidField("productId", "validation.required");
        }
        boolean normalized = Boolean.parseBoolean(p.get("normalized"));
        var key = f + "|" + normalized;
        LongFunction<Object> compute = v -> service.priceTable(f, normalized, v);
        return respond(service.serve("price-table", key, inm, compute));
    }

    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rebuild() {
        projection.rebuildAll();
    }

    private static ResponseEntity<byte[]> respond(AnalyticsService.Payload payload) {
        var builder = ResponseEntity.status(payload.body() == null ? HttpStatus.NOT_MODIFIED : HttpStatus.OK)
                .eTag(payload.etag()).cacheControl(CacheControl.noCache().cachePrivate());
        return payload.body() == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(payload.body());
    }
}
