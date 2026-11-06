package com.accountbook.product.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.product.api.ProductDraft;
import com.accountbook.product.api.event.ProductChangedEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private TestDb db;
    private ProductService service;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        db = TestDb.create("product");
        service = new ProductService(new ProductRepository(db.jdbc()), events::add);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static ProductDraft milk(String spec, String... tags) {
        return new ProductDraft("明治牛奶", "明治", "BEVERAGE", spec, null, null, null, List.of(tags));
    }

    @Test
    void specTextIsParsedIntoDimensionAndBaseQuantity() {
        var kg = service.create(new ProductDraft("大米", "秋田", "FOOD", "2kg", null, null, null, null));
        assertThat(kg.specDim()).isEqualTo("WEIGHT");
        assertThat(kg.specBaseQty()).isEqualTo(2000.0);

        var litre = service.create(milk("1L"));
        assertThat(litre.specDim()).isEqualTo("VOLUME");
        assertThat(litre.specBaseQty()).isEqualTo(1000.0);

        var pack = service.create(new ProductDraft("鸡蛋", null, "FRESH", "10个", null, null, null, null));
        assertThat(pack.specDim()).isEqualTo("COUNT");
        assertThat(pack.specBaseQty()).isEqualTo(10.0);
        assertThat(events).hasSize(3).allMatch(e -> e instanceof ProductChangedEvent);
    }

    @Test
    void equivalentSpecsShareIdentityButDifferentSizesDoNot() {
        var litre = service.findOrCreate(milk("1L"));
        var sameInMl = service.findOrCreate(new ProductDraft("明治牛奶", "明治", "BEVERAGE", null, 1000.0, "ml", null, null));
        var half = service.findOrCreate(milk("500ml"));

        assertThat(sameInMl.id()).isEqualTo(litre.id());
        assertThat(half.id()).isNotEqualTo(litre.id());
    }

    @Test
    void unstructuredSpecTextIsKeptWithoutDimension() {
        var p = service.create(milk("大盒装"));
        assertThat(p.specText()).isEqualTo("大盒装");
        assertThat(p.specDim()).isNull();
        assertThat(p.specBaseQty()).isNull();
        assertThat(p.specValue()).isNull();
    }

    @Test
    void valueWithoutUnitOrNonPositiveValueIsRejected() {
        assertThatThrownBy(() -> service.create(new ProductDraft("x", null, null, null, 5.0, null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(new ProductDraft("x", null, null, null, 0.0, "g", null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tagsAreNormalizedDedupedAndReplacedOnUpdate() {
        var p = service.create(milk("1L", " 牛奶 ", "牛奶", "Cold", ""));
        assertThat(p.tags()).containsExactlyInAnyOrder("牛奶", "cold");

        var updated = service.update(p.id(), milk("1L", "促销"));
        assertThat(updated.tags()).containsExactly("促销");
        assertThat(service.tags("促", 10)).containsExactly("促销");
        assertThat(service.tags("牛", 10)).isEmpty();
    }

    @Test
    void duplicateCreateConflictsAndUpdateCannotCollide() {
        service.create(milk("1L"));
        assertThatThrownBy(() -> service.create(milk("1000ml")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CONFLICT"));

        var other = service.create(milk("500ml"));
        assertThatThrownBy(() -> service.update(other.id(), milk("1L"))).isInstanceOf(ApiException.class);
    }

    @Test
    void searchByBrandCategoryAndTag() {
        service.create(milk("1L", "牛奶"));
        service.create(new ProductDraft("面包", "山崎", "FOOD", null, null, null, null, List.of("早餐")));

        assertThat(service.search(null, "明治", null, null, 0, 20).total()).isEqualTo(1);
        assertThat(service.search(null, null, "FOOD", null, 0, 20).items()).extracting("name").containsExactly("面包");
        assertThat(service.search(null, null, null, "牛奶", 0, 20).items()).extracting("name").containsExactly("明治牛奶");
        assertThat(service.brands("山", 10)).containsExactly("山崎");
    }

    @Test
    void likeWildcardsInSuggestionsAreEscaped() {
        service.create(new ProductDraft("a", "100%纯", "FOOD", null, null, null, null, null));
        service.create(new ProductDraft("b", "其它", "FOOD", null, null, null, null, null));
        assertThat(service.brands("%", 10)).containsExactly("100%纯");
    }

    @Test
    void findAllAndMatch() {
        var a = service.create(milk("1L"));
        var b = service.create(new ProductDraft("面包", null, null, null, null, null, null, null));

        assertThat(service.findAll(List.of(a.id(), b.id(), 999L))).containsOnlyKeys(a.id(), b.id());
        assertThat(service.match("牛奶", 5)).extracting("id").containsExactly(a.id());
        assertThat(service.match("", 5)).isEmpty();
    }
}
