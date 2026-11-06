package com.accountbook.analytics.internal;

import com.accountbook.merchant.api.MerchantView;
import com.accountbook.product.api.ProductView;
import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.ReceiptItemFact;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProjectionRepository {

    record Bucket(String date, String currency, long merchantId, long productId) {
    }

    private final JdbcClient jdbc;

    ProjectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long version() {
        return jdbc.sql("SELECT v FROM ana_meta WHERE k = 'version'").query(Long.class).single();
    }

    void bumpVersion() {
        jdbc.sql("UPDATE ana_meta SET v = v + 1 WHERE k = 'version'").update();
    }

    long revision() {
        return jdbc.sql("SELECT v FROM ana_meta WHERE k = 'projection_rev'").query(Long.class).single();
    }

    void setRevision(long rev) {
        jdbc.sql("UPDATE ana_meta SET v = :v WHERE k = 'projection_rev'").param("v", rev).update();
    }

    void upsertMerchant(MerchantView m) {
        jdbc.sql("""
                INSERT INTO ana_merchant_dim (id, name, country, city, district, type)
                VALUES (:id, :name, :country, :city, :district, :type)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name, country = excluded.country, city = excluded.city,
                    district = excluded.district, type = excluded.type""")
                .param("id", m.id()).param("name", m.name()).param("country", m.country()).param("city", m.city())
                .param("district", m.district()).param("type", m.type()).update();
    }

    void upsertProduct(ProductView p) {
        jdbc.sql("""
                INSERT INTO ana_product_dim (id, name, brand, category, spec_dim, spec_base_qty)
                VALUES (:id, :name, :brand, :category, :dim, :qty)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name, brand = excluded.brand,
                    category = excluded.category, spec_dim = excluded.spec_dim, spec_base_qty = excluded.spec_base_qty""")
                .param("id", p.id()).param("name", p.name()).param("brand", p.brand()).param("category", p.category())
                .param("dim", p.specDim()).param("qty", p.specBaseQty()).update();
        jdbc.sql("DELETE FROM ana_product_tag WHERE product_id = :id").param("id", p.id()).update();
        for (String tag : p.tags()) {
            jdbc.sql("INSERT OR IGNORE INTO ana_product_tag (product_id, tag) VALUES (:id, :tag)")
                    .param("id", p.id()).param("tag", tag).update();
        }
    }

    List<Bucket> bucketsOfReceipt(long receiptId) {
        return jdbc.sql("""
                SELECT DISTINCT purchased_date, currency, merchant_id, product_id
                FROM ana_price_fact WHERE receipt_id = :id""")
                .param("id", receiptId)
                .query((rs, i) -> new Bucket(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4))).list();
    }

    void deleteFacts(long receiptId) {
        jdbc.sql("DELETE FROM ana_price_fact WHERE receipt_id = :id").param("id", receiptId).update();
    }

    void insertFacts(ReceiptFact f) {
        for (ReceiptItemFact it : f.items()) {
            double unit = it.quantity() > 0 ? it.amountMinor() / it.quantity() : it.unitPriceMinor();
            jdbc.sql("""
                    INSERT INTO ana_price_fact (receipt_id, purchased_date, currency, merchant_id, product_id,
                                                quantity, unit_minor, amount_minor)
                    VALUES (:r, :d, :c, :m, :p, :q, :u, :a)""")
                    .param("r", f.id()).param("d", f.purchasedDate()).param("c", f.currency())
                    .param("m", f.merchantId()).param("p", it.productId()).param("q", it.quantity())
                    .param("u", unit).param("a", it.amountMinor()).update();
        }
    }

    void rebuildBuckets(Collection<Bucket> buckets) {
        for (Bucket b : buckets) {
            jdbc.sql("""
                    DELETE FROM ana_price_daily
                    WHERE purchased_date = :d AND currency = :c AND merchant_id = :m AND product_id = :p""")
                    .param("d", b.date()).param("c", b.currency()).param("m", b.merchantId())
                    .param("p", b.productId()).update();
            jdbc.sql(DAILY_SELECT + " AND purchased_date = :d AND currency = :c AND merchant_id = :m AND product_id = :p"
                            + DAILY_GROUP)
                    .param("d", b.date()).param("c", b.currency()).param("m", b.merchantId())
                    .param("p", b.productId()).update();
        }
    }

    private static final String DAILY_SELECT = """
            INSERT INTO ana_price_daily (product_id, merchant_id, currency, purchased_date, n, qty, amount_minor,
                                         min_unit, max_unit, sum_unit)
            SELECT product_id, merchant_id, currency, purchased_date, COUNT(*), SUM(quantity), SUM(amount_minor),
                   MIN(unit_minor), MAX(unit_minor), SUM(unit_minor)
            FROM ana_price_fact WHERE 1 = 1""";
    private static final String DAILY_GROUP = " GROUP BY product_id, merchant_id, currency, purchased_date";

    void clearAll() {
        jdbc.sql("DELETE FROM ana_price_daily").update();
        jdbc.sql("DELETE FROM ana_price_fact").update();
        jdbc.sql("DELETE FROM ana_product_tag").update();
        jdbc.sql("DELETE FROM ana_product_dim").update();
        jdbc.sql("DELETE FROM ana_merchant_dim").update();
    }

    void rebuildAllDaily() {
        jdbc.sql(DAILY_SELECT + DAILY_GROUP).update();
    }
}
