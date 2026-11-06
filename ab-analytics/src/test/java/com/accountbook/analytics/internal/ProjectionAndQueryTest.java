package com.accountbook.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.accountbook.common.testing.TestDb;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.merchant.api.event.MerchantChangedEvent;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.ReceiptApi;
import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.ReceiptItemFact;
import com.accountbook.receipt.api.event.ReceiptDeletedEvent;
import com.accountbook.receipt.api.event.ReceiptSavedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real SQLite for ana_*; merchants, products and receipts are served by stand-ins for the other modules' APIs. */
class ProjectionAndQueryTest {

    private static final long SEIYU = 1;
    private static final long OK = 2;
    private static final long MILK_1L = 10;
    private static final long MILK_500 = 11;
    private static final long BREAD = 12;

    private TestDb db;
    private ProjectionRepository repo;
    private ProjectionService projection;
    private AnalyticsService service;
    private final List<ReceiptFact> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        db = TestDb.create("analytics");
        repo = new ProjectionRepository(db.jdbc());

        var merchants = mock(MerchantApi.class);
        when(merchants.find(SEIYU)).thenReturn(Optional.of(merchant(SEIYU, "西友", "东京", "涩谷")));
        when(merchants.find(OK)).thenReturn(Optional.of(merchant(OK, "OK", "东京", "新宿")));
        var products = mock(ProductApi.class);
        when(products.findAll(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            Map<Long, ProductView> all = Map.of(
                    MILK_1L, product(MILK_1L, "牛奶1L", "明治", "VOLUME", 1000.0, List.of("牛奶")),
                    MILK_500, product(MILK_500, "牛奶500", "明治", "VOLUME", 500.0, List.of("牛奶")),
                    BREAD, product(BREAD, "面包", "山崎", null, null, List.of()));
            Map<Long, ProductView> out = new java.util.HashMap<>();
            ids.forEach(i -> out.put(i, all.get(i)));
            return out;
        });
        ReceiptApi receipts = new ReceiptApi() {
            @Override
            public Optional<ReceiptFact> findFact(long id) {
                return stored.stream().filter(f -> f.id() == id).findFirst();
            }

            @Override
            public void forEachFact(java.util.function.Consumer<ReceiptFact> consumer) {
                stored.forEach(consumer);
            }
        };
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource()));
        projection = new ProjectionService(repo, merchants, products, receipts, tx);
        service = new AnalyticsService(new QueryRepository(db.jdbc()), repo, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static MerchantView merchant(long id, String name, String city, String district) {
        return new MerchantView(id, name, "JP", city, district, "SUPERMARKET", null, null, null, 0);
    }

    private static ProductView product(long id, String name, String brand, String dim, Double base, List<String> tags) {
        return new ProductView(id, name, brand, "FOOD", null, null, null, dim, base, null, tags, 0);
    }

    private static ReceiptFact receipt(long id, long merchantId, String date, ReceiptItemFact... items) {
        return new ReceiptFact(id, merchantId, date + "T12:00", date, "JPY", 0, 0, List.of(items));
    }

    private static ReceiptItemFact line(long productId, double qty, long unit, long amount) {
        return new ReceiptItemFact(productId, qty, unit, 0, amount);
    }

    private void save(ReceiptFact f) {
        stored.removeIf(s -> s.id() == f.id());
        stored.add(f);
        projection.on(new ReceiptSavedEvent(f));
    }

    private void delete(long id) {
        stored.removeIf(s -> s.id() == id);
        projection.on(new ReceiptDeletedEvent(id));
    }

    private ChartResponse compareMerchants(String productId, boolean normalized) {
        var f = Filter.parse(Map.of("productId", productId), "JPY");
        return service.priceCompare(f, "merchant", normalized, repo.version());
    }

    private long count(String sql) {
        return db.jdbc().sql(sql).query(Long.class).single();
    }

    @Test
    void savingAReceiptProjectsFactsDailyRollupAndDimensions() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 2, 198, 396), line(BREAD, 1, 150, 150)));

        assertThat(count("SELECT COUNT(*) FROM ana_price_fact")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ana_price_daily")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ana_merchant_dim")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ana_product_dim")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM ana_product_tag")).isEqualTo(1);
        assertThat(repo.version()).isEqualTo(1);
    }

    @Test
    void resavingIsIdempotentAndReplacesOldLines() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200)));
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200)));
        assertThat(count("SELECT COUNT(*) FROM ana_price_fact")).isEqualTo(1);

        // edited: different date, different product - the old buckets must disappear
        save(receipt(1, SEIYU, "2026-11-03", line(BREAD, 1, 150, 150)));
        assertThat(count("SELECT COUNT(*) FROM ana_price_fact")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ana_price_daily WHERE product_id = " + MILK_1L)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ana_price_daily WHERE product_id = " + BREAD
                + " AND purchased_date = '2026-11-03'")).isEqualTo(1);
    }

    @Test
    void dailyRollupMinMaxAreRecomputedAfterDelete() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 100, 100)));
        save(receipt(2, SEIYU, "2026-11-01", line(MILK_1L, 1, 300, 300)));

        var both = compareMerchants("" + MILK_1L, false);
        assertThat(both.series().get(1).data()).singleElement().satisfies(v -> assertThat(v).isEqualByComparingTo("100"));
        assertThat(both.series().get(2).data()).singleElement().satisfies(v -> assertThat(v).isEqualByComparingTo("300"));
        assertThat(both.series().get(0).data()).singleElement().satisfies(v -> assertThat(v).isEqualByComparingTo("200"));

        delete(2);
        var one = compareMerchants("" + MILK_1L, false);
        assertThat(one.series().get(2).data()).singleElement().satisfies(v -> assertThat(v).isEqualByComparingTo("100"));
        assertThat(one.counts()).containsExactly(1L);

        delete(1);
        assertThat(count("SELECT COUNT(*) FROM ana_price_daily")).isZero();
        assertThat(compareMerchants("" + MILK_1L, false).labels()).isEmpty();
    }

    @Test
    void compareOrdersCheapestMerchantFirst() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 220, 220)));
        save(receipt(2, OK, "2026-11-01", line(MILK_1L, 1, 180, 180)));

        var chart = compareMerchants("" + MILK_1L, false);
        assertThat(chart.labels()).containsExactly("OK", "西友");
        assertThat(chart.unit()).isEqualTo("item");
    }

    @Test
    void normalizedPriceUsesPer100mlAndIgnoresProductsWithoutSpec() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200), line(BREAD, 1, 150, 150)));
        save(receipt(2, OK, "2026-11-01", line(MILK_500, 1, 110, 110)));

        var f = Filter.parse(Map.of("brand", "明治"), "JPY");
        var chart = service.priceCompare(f, "merchant", true, repo.version());
        assertThat(chart.unit()).isEqualTo("100ml");
        assertThat(chart.labels()).containsExactly("西友", "OK");
        assertThat(chart.series().get(0).data().get(0)).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(chart.series().get(0).data().get(1)).isEqualByComparingTo(new BigDecimal("22.00"));

        // bread has no spec, so a normalized view of it has nothing to compare
        var breadOnly = Filter.parse(Map.of("productId", "" + BREAD), "JPY");
        assertThat(service.priceCompare(breadOnly, "merchant", true, repo.version()).labels()).isEmpty();
    }

    @Test
    void discountedLinesUseNetUnitPrice() {
        // 2 x 198 with 20 off: net 376, so 188 each
        save(receipt(1, SEIYU, "2026-11-01", new ReceiptItemFact(MILK_1L, 2, 198, 20, 376)));
        var chart = compareMerchants("" + MILK_1L, false);
        assertThat(chart.series().get(0).data().get(0)).isEqualByComparingTo("188");
    }

    @Test
    void trendBucketsByMonthAndLeavesGapsNull() {
        save(receipt(1, SEIYU, "2026-10-05", line(MILK_1L, 1, 200, 200)));
        save(receipt(2, OK, "2026-11-05", line(MILK_1L, 1, 180, 180)));

        var f = Filter.parse(Map.of("productId", "" + MILK_1L), "JPY");
        var chart = service.priceTrend(f, List.of(), "month", "avg", false, repo.version());
        assertThat(chart.labels()).containsExactly("2026-10", "2026-11");
        assertThat(chart.series()).hasSize(2);
        var seiyu = chart.series().stream().filter(s -> s.key().equals("m:" + SEIYU)).findFirst().orElseThrow();
        assertThat(seiyu.data().get(0)).isEqualByComparingTo("200");
        assertThat(seiyu.data().get(1)).isNull();
    }

    @Test
    void spendingTotalsByMerchantAndCountsDistinctReceipts() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200), line(BREAD, 1, 150, 150)));
        save(receipt(2, OK, "2026-11-02", line(BREAD, 2, 100, 200)));

        var chart = service.spending(Filter.parse(Map.of(), "JPY"), "merchant", 10, repo.version());
        assertThat(chart.labels()).containsExactly("西友", "OK");
        assertThat(chart.total()).isEqualByComparingTo("550");
        assertThat(chart.count()).isEqualTo(2);
    }

    @Test
    void merchantRenameReachesChartsAndBumpsVersion() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200)));
        long before = repo.version();

        projection.on(new MerchantChangedEvent(merchant(SEIYU, "西友 新名", "东京", "涩谷")));

        assertThat(repo.version()).isEqualTo(before + 1);
        assertThat(compareMerchants("" + MILK_1L, false).labels()).containsExactly("西友 新名");
    }

    @Test
    void etagFollowsDataVersionAndConditionalRequestsSkipCompute() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200)));
        var computes = new AtomicInteger();

        var first = service.serve("compare", "k", null, v -> {
            computes.incrementAndGet();
            return Map.of("version", v);
        });
        assertThat(first.body()).isNotNull();
        assertThat(first.etag()).startsWith("\"v1-");

        // same version, same query: served from the cache, no recompute
        service.serve("compare", "k", null, v -> {
            computes.incrementAndGet();
            return Map.of();
        });
        assertThat(computes).hasValue(1);

        // validator still current: 304, body null, no compute
        var notModified = service.serve("compare", "k", first.etag(), v -> {
            computes.incrementAndGet();
            return Map.of();
        });
        assertThat(notModified.body()).isNull();
        assertThat(computes).hasValue(1);

        // a write bumps the version, so the old validator no longer matches and the query recomputes
        save(receipt(2, OK, "2026-11-02", line(MILK_1L, 1, 180, 180)));
        var fresh = service.serve("compare", "k", first.etag(), v -> {
            computes.incrementAndGet();
            return Map.of("version", v);
        });
        assertThat(fresh.body()).isNotNull();
        assertThat(fresh.etag()).isNotEqualTo(first.etag());
        assertThat(computes).hasValue(2);
    }

    @Test
    void rebuildRestoresTheProjectionFromReceiptsAndStampsTheRevision() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 100, 100)));
        save(receipt(2, OK, "2026-11-02", line(MILK_1L, 1, 300, 300)));
        var before = compareMerchants("" + MILK_1L, false);
        assertThat(repo.revision()).isZero();

        db.jdbc().sql("DELETE FROM ana_price_daily").update();
        db.jdbc().sql("DELETE FROM ana_merchant_dim").update();
        assertThat(compareMerchants("" + MILK_1L, false).labels()).isEmpty();

        projection.rebuildAll();

        var after = compareMerchants("" + MILK_1L, false);
        assertThat(after.labels()).isEqualTo(before.labels());
        assertThat(after.series()).isEqualTo(before.series());
        assertThat(repo.revision()).isEqualTo(ProjectionService.REVISION);
    }

    @Test
    void staleRevisionTriggersRebuildAtStartupButCurrentRevisionDoesNot() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 100, 100)));
        projection.rebuildIfStale();
        assertThat(repo.revision()).isEqualTo(ProjectionService.REVISION);

        long version = repo.version();
        projection.rebuildIfStale();
        assertThat(repo.version()).isEqualTo(version);
    }

    @Test
    void facetsListOnlyValuesThatHaveData() {
        save(receipt(1, SEIYU, "2026-11-01", line(MILK_1L, 1, 200, 200)));

        var facets = service.facets(repo.version());
        assertThat(facets.get("currencies")).isEqualTo(List.of("JPY"));
        assertThat(facets.get("brands")).isEqualTo(List.of("明治"));
        assertThat(facets.get("tags")).isEqualTo(List.of("牛奶"));
        assertThat(facets.get("countries")).isEqualTo(List.of("JP"));
        assertThat(((Map<?, ?>) facets.get("dateRange")).get("min")).isEqualTo("2026-11-01");
    }
}
