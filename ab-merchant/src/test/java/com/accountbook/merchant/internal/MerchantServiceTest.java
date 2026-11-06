package com.accountbook.merchant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accountbook.common.testing.TestDb;
import com.accountbook.common.web.ApiException;
import com.accountbook.merchant.api.MerchantDraft;
import com.accountbook.merchant.api.event.MerchantChangedEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantServiceTest {

    private TestDb db;
    private MerchantService service;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        db = TestDb.create("merchant");
        service = new MerchantService(new MerchantRepository(db.jdbc()), events::add);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static MerchantDraft draft(String name, String city) {
        return new MerchantDraft(name, "jp", city, "涩谷", "SUPERMARKET", null, null, null);
    }

    @Test
    void createStoresAreaAttributesAndPublishesEvent() {
        var saved = service.create(new MerchantDraft("西友 涩谷店", "jp", "东京", "涩谷", "SUPERMARKET", "addr", 35.0, 139.0));

        assertThat(saved.country()).isEqualTo("JP");
        assertThat(saved.district()).isEqualTo("涩谷");
        assertThat(saved.latitude()).isEqualTo(35.0);
        assertThat(saved.longitude()).isEqualTo(139.0);
        assertThat(events).singleElement().isInstanceOf(MerchantChangedEvent.class);
    }

    @Test
    void coordinatesMayBeAbsent() {
        var saved = service.create(draft("无坐标商店", "东京"));
        assertThat(saved.latitude()).isNull();
        assertThat(saved.longitude()).isNull();
    }

    @Test
    void createRejectsDuplicateNameInSameCityButAllowsOtherCity() {
        service.create(draft("OK Store", "东京"));

        assertThatThrownBy(() -> service.create(draft("ＯＫ store", "东京")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CONFLICT"));
        assertThat(service.create(draft("OK Store", "大阪")).id()).isPositive();
    }

    @Test
    void findOrCreateIsIdempotentAndQuiet() {
        var first = service.findOrCreate(draft("Lawson", "东京"));
        var second = service.findOrCreate(draft("lawson", "东京"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(events).hasSize(1);
    }

    @Test
    void updateRejectsCollisionWithAnotherMerchant() {
        service.create(draft("A", "东京"));
        var b = service.create(draft("B", "东京"));

        assertThatThrownBy(() -> service.update(b.id(), draft("A", "东京"))).isInstanceOf(ApiException.class);
        var renamed = service.update(b.id(), draft("B2", "东京"));
        assertThat(renamed.name()).isEqualTo("B2");
        assertThat(events).hasSize(3);
    }

    @Test
    void invalidTypeAndBlankNameAreRejected() {
        assertThatThrownBy(() -> service.create(new MerchantDraft("X", null, null, null, "NOPE", null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create(new MerchantDraft("   ", null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requireThrowsNotFound() {
        assertThatThrownBy(() -> service.require(999))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void matchPrefersExactNameThenClosestLength() {
        service.create(draft("西友", "东京"));
        service.create(draft("西友 涩谷店", "东京"));

        var matches = service.match("西友", 5);
        assertThat(matches).extracting("name").containsExactly("西友", "西友 涩谷店");
        assertThat(service.match("  ", 5)).isEmpty();
    }

    @Test
    void searchFiltersAndPaginates() {
        service.create(draft("A", "东京"));
        service.create(draft("B", "东京"));
        service.create(new MerchantDraft("C", "cn", "上海", "浦东", "CAFE", null, null, null));

        assertThat(service.search(null, "jp", null, null, null, 0, 20).total()).isEqualTo(2);
        assertThat(service.search(null, null, "上海", null, "CAFE", 0, 20).items()).extracting("name").containsExactly("C");
        var page = service.search(null, null, null, null, null, 0, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(service.search("b", null, null, null, null, 0, 20).items()).extracting("name").containsExactly("B");
    }
}
