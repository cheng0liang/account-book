package com.accountbook.receipt.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.config.AppProperties;
import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.merchant.api.MerchantApi;
import com.accountbook.merchant.api.MerchantDraft;
import com.accountbook.merchant.api.MerchantView;
import com.accountbook.merchant.api.event.MerchantChangedEvent;
import com.accountbook.product.api.ProductApi;
import com.accountbook.product.api.ProductDraft;
import com.accountbook.product.api.ProductView;
import com.accountbook.product.api.event.ProductChangedEvent;
import com.accountbook.receipt.api.event.ReceiptDeletedEvent;
import com.accountbook.receipt.api.event.ReceiptSavedEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real SQLite for the receipt tables; merchant/product are in-memory stand-ins for the other modules' APIs. */
class ReceiptServiceTest {

    private TestDb db;
    private Path dataDir;
    private ReceiptService service;
    private ReceiptRepository repo;
    private ImageStore images;
    private TransactionTemplate tx;
    private final List<Object> events = new ArrayList<>();
    private final FakeMerchants merchants = new FakeMerchants();
    private final FakeProducts products = new FakeProducts();

    @BeforeEach
    void setUp() throws Exception {
        db = TestDb.create("receipt");
        dataDir = Files.createTempDirectory("ab-img-");
        images = new ImageStore(new AppProperties(dataDir.toString(), "JPY", List.of("JPY", "CNY"), null));
        repo = new ReceiptRepository(db.jdbc());
        service = new ReceiptService(repo, new ReceiptResolver(merchants, products, images), merchants, products,
                images, events::add);
        tx = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource()));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static ReceiptForm.MerchantRef merchant(String name) {
        return new ReceiptForm.MerchantRef(null, name, "JP", "东京", null, "SUPERMARKET", null, 35.0, 139.0);
    }

    private static ReceiptForm.ItemForm item(String name, String qty, String unit, String discount) {
        return new ReceiptForm.ItemForm(null, new ReceiptForm.ProductRef(null, name, "明治", "FOOD", "1L", null, null, null, List.of()),
                new BigDecimal(qty), new BigDecimal(unit), discount == null ? null : new BigDecimal(discount), null, null);
    }

    private static ReceiptForm form(String currency, ReceiptForm.ItemForm... items) {
        return new ReceiptForm(null, merchant("西友"), "2026-11-06T12:30", currency, null, null, null, "CASH", null,
                "MANUAL", null, null, List.of(items));
    }

    private ReceiptForm create(ReceiptForm f) {
        return tx.execute(s -> service.create(f));
    }

    @Test
    void createComputesAmountsTotalAndPublishesFact() {
        var saved = create(form("JPY", item("牛奶", "2", "198", "20"), item("面包", "1", "150", null)));

        assertThat(saved.items().get(0).amount()).isEqualByComparingTo("376");
        assertThat(saved.totalAmount()).isEqualByComparingTo("526");
        assertThat(saved.taxAmount()).isNull();
        assertThat(saved.purchasedAt()).isEqualTo("2026-11-06T12:30");
        assertThat(saved.items()).extracting(i -> i.product().name()).containsExactly("牛奶", "面包");

        var event = events.stream().filter(ReceiptSavedEvent.class::isInstance).map(ReceiptSavedEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(event.receipt().purchasedDate()).isEqualTo("2026-11-06");
        assertThat(event.receipt().items()).extracting("amountMinor").containsExactly(376L, 150L);
    }

    @Test
    void minorUnitsFollowTheCurrencyDecimals() {
        var saved = create(form("CNY", item("牛奶", "1", "12.50", null)));

        assertThat(saved.items().get(0).unitPrice()).isEqualByComparingTo("12.5");
        assertThat(saved.totalAmount()).isEqualByComparingTo("12.5");
        assertThat(repo.findById(saved.id()).orElseThrow().totalMinor()).isEqualTo(1250L);
    }

    @Test
    void nullableTaxRoundTripsWithoutTypeErrors() {
        var withTax = new ReceiptForm(null, merchant("西友"), "2026-11-06T12:30", "JPY", null, new BigDecimal("8"), null,
                null, null, "MANUAL", null, null, List.of(item("牛奶", "1", "100", null)));
        assertThat(create(withTax).taxAmount()).isEqualByComparingTo("8");
        assertThat(create(form("JPY", item("牛奶", "1", "100", null))).taxAmount()).isNull();
    }

    @Test
    void validationErrorsAreFieldLevel() {
        assertThatThrownBy(() -> create(form("XXX", item("a", "1", "1", null)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> create(form("JPY"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> create(form("JPY", item("a", "0", "1", null)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> create(form("JPY", item("a", "1", "-5", null)))).isInstanceOf(ApiException.class);
        var badDate = new ReceiptForm(null, merchant("x"), "yesterday", "JPY", null, null, null, null, null, null, null,
                null, List.of(item("a", "1", "1", null)));
        assertThatThrownBy(() -> create(badDate)).isInstanceOf(ApiException.class);
        var badImage = new ReceiptForm(null, merchant("x"), "2026-11-06T12:30", "JPY", null, null, null, null, null, null,
                "../../etc/passwd", null, List.of(item("a", "1", "1", null)));
        assertThatThrownBy(() -> create(badImage)).isInstanceOf(ApiException.class);
        assertThat(repo.count(new ReceiptRepository.Filter(null, null, null, null))).isZero();
    }

    @Test
    void updateReplacesItemsAndDeleteRemovesEverything() {
        var saved = create(form("JPY", item("牛奶", "1", "198", null), item("面包", "1", "150", null)));

        var edited = tx.execute(s -> service.update(saved.id(), form("JPY", item("牛奶", "1", "180", null))));
        assertThat(edited.items()).hasSize(1);
        assertThat(edited.totalAmount()).isEqualByComparingTo("180");
        assertThat(service.list(null, null, null, null, 0, 20).items().get(0).itemCount()).isEqualTo(1);

        tx.executeWithoutResult(s -> service.delete(saved.id()));
        assertThat(events).anyMatch(ReceiptDeletedEvent.class::isInstance);
        assertThat(repo.items(saved.id())).isEmpty();
        assertThatThrownBy(() -> service.get(saved.id())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.delete(saved.id()))).isInstanceOf(ApiException.class);
    }

    @Test
    void listFiltersByDateMerchantAndTextAndOrdersNewestFirst() {
        var older = new ReceiptForm(null, merchant("西友"), "2026-10-01T09:00", "JPY", null, null, null, null, null, null,
                null, null, List.of(item("牛奶", "1", "100", null)));
        var newer = new ReceiptForm(null, merchant("OK"), "2026-11-05T09:00", "JPY", null, null, null, null, null, null,
                null, null, List.of(item("面包", "1", "100", null)));
        var a = create(older);
        var b = create(newer);

        var all = service.list(null, null, null, null, 0, 20);
        assertThat(all.items()).extracting("id").containsExactly(b.id(), a.id());
        assertThat(service.list("2026-11-01", null, null, null, 0, 20).total()).isEqualTo(1);
        assertThat(service.list(null, "2026-10-31", null, null, 0, 20).items()).extracting("id").containsExactly(a.id());
        assertThat(service.list(null, null, b.merchant().id(), null, 0, 20).items()).extracting("id").containsExactly(b.id());
        assertThat(service.list(null, null, null, "面包", 0, 20).items()).extracting("id").containsExactly(b.id());
        assertThat(service.list(null, null, null, "%", 0, 20).total()).isZero();
        assertThat(all.items().get(0).firstItems()).containsExactly("面包");
    }

    @Test
    void renamesPropagateToDenormalisedNames() {
        var saved = create(form("JPY", item("牛奶", "1", "100", null)));
        var sync = new ReceiptNameSync(repo);
        sync.on(new MerchantChangedEvent(new MerchantView(saved.merchant().id(), "西友 新名", null, null, null, null,
                null, null, null, 0)));
        sync.on(new ProductChangedEvent(new ProductView(saved.items().get(0).product().id(), "牛奶 新名", null, null, null,
                null, null, null, null, null, List.of(), 0)));

        assertThat(service.list(null, null, null, null, 0, 20).items().get(0).merchantName()).isEqualTo("西友 新名");
        assertThat(repo.items(saved.id()).get(0).productName()).isEqualTo("牛奶 新名");
    }

    @Test
    void factExposesNetLineAmountsForAnalytics() {
        var saved = create(form("JPY", item("牛奶", "2", "198", "20")));
        var fact = service.findFact(saved.id()).orElseThrow();

        assertThat(fact.currency()).isEqualTo("JPY");
        assertThat(fact.items()).singleElement().satisfies(i -> {
            assertThat(i.quantity()).isEqualTo(2.0);
            assertThat(i.unitPriceMinor()).isEqualTo(198L);
            assertThat(i.amountMinor()).isEqualTo(376L);
        });
        List<Long> seen = new ArrayList<>();
        service.forEachFact(f -> seen.add(f.id()));
        assertThat(seen).containsExactly(saved.id());
    }

    private static final class FakeMerchants implements MerchantApi {
        private final AtomicLong ids = new AtomicLong();
        private final Map<String, MerchantView> byName = new HashMap<>();
        private final Map<Long, MerchantView> byId = new HashMap<>();

        @Override
        public Optional<MerchantView> find(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public MerchantView require(long id) {
            return find(id).orElseThrow(() -> ApiException.notFound("merchant.notFound", id));
        }

        @Override
        public MerchantView findOrCreate(MerchantDraft d) {
            return byName.computeIfAbsent(d.name(), n -> {
                var m = new MerchantView(ids.incrementAndGet(), n, d.country(), d.city(), d.district(), d.type(),
                        d.address(), d.latitude(), d.longitude(), 0);
                byId.put(m.id(), m);
                return m;
            });
        }

        @Override
        public List<MerchantView> match(String name, int limit) {
            return List.of();
        }
    }

    private static final class FakeProducts implements ProductApi {
        private final AtomicLong ids = new AtomicLong();
        private final Map<String, ProductView> byName = new HashMap<>();
        private final Map<Long, ProductView> byId = new HashMap<>();

        @Override
        public Optional<ProductView> find(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public ProductView require(long id) {
            return find(id).orElseThrow(() -> ApiException.notFound("product.notFound", id));
        }

        @Override
        public Map<Long, ProductView> findAll(Collection<Long> ids) {
            Map<Long, ProductView> out = new HashMap<>();
            ids.forEach(i -> find(i).ifPresent(p -> out.put(i, p)));
            return out;
        }

        @Override
        public ProductView findOrCreate(ProductDraft d) {
            return byName.computeIfAbsent(d.name(), n -> {
                var p = new ProductView(ids.incrementAndGet(), n, d.brand(), d.category(), d.specText(), null, null, null,
                        null, d.barcode(), d.tags() == null ? List.of() : d.tags(), 0);
                byId.put(p.id(), p);
                return p;
            });
        }

        @Override
        public List<ProductView> match(String name, int limit) {
            return List.of();
        }
    }
}
