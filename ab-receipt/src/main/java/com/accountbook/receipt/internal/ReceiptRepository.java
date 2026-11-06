package com.accountbook.receipt.internal;

import static com.accountbook.common.db.Rows.nullableLong;

import com.accountbook.receipt.api.ReceiptFact;
import com.accountbook.receipt.api.ReceiptItemFact;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReceiptRepository {

    private static final int FACT_BATCH = 500;
    private static final String COLUMNS = "id, merchant_id, merchant_name, purchased_at, purchased_date, currency, "
            + "total_minor, tax_minor, discount_minor, payment_method, note, source, image_path, item_count";

    record Row(long id, long merchantId, String merchantName, String purchasedAt, String purchasedDate, String currency,
               long totalMinor, Long taxMinor, long discountMinor, String paymentMethod, String note, String source,
               String imagePath, int itemCount) {
    }

    record ItemRow(long id, long receiptId, long productId, String productName, double quantity, long unitPriceMinor,
                   long discountMinor, long amountMinor, String note) {
    }

    record NewReceipt(long merchantId, String merchantName, String purchasedAt, String purchasedDate, String currency,
                      long totalMinor, Long taxMinor, long discountMinor, String paymentMethod, String note,
                      String source, String imagePath, int itemCount) {
    }

    record NewItem(long productId, String productName, double quantity, long unitPriceMinor, long discountMinor,
                   long amountMinor, String note) {
    }

    private static final RowMapper<Row> ROW = (rs, i) -> new Row(rs.getLong("id"), rs.getLong("merchant_id"),
            rs.getString("merchant_name"), rs.getString("purchased_at"), rs.getString("purchased_date"),
            rs.getString("currency"), rs.getLong("total_minor"), nullableLong(rs, "tax_minor"),
            rs.getLong("discount_minor"), rs.getString("payment_method"), rs.getString("note"),
            rs.getString("source"), rs.getString("image_path"), rs.getInt("item_count"));

    private static final RowMapper<ItemRow> ITEM = (rs, i) -> new ItemRow(rs.getLong("id"), rs.getLong("receipt_id"),
            rs.getLong("product_id"), rs.getString("product_name"), rs.getDouble("quantity"),
            rs.getLong("unit_price_minor"), rs.getLong("discount_minor"), rs.getLong("amount_minor"),
            rs.getString("note"));

    private final JdbcClient jdbc;

    ReceiptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM rcp_receipt WHERE id = :id").param("id", id).query(ROW).optional();
    }

    List<ItemRow> items(long receiptId) {
        return jdbc.sql("SELECT * FROM rcp_receipt_item WHERE receipt_id = :id ORDER BY line_no")
                .param("id", receiptId).query(ITEM).list();
    }

    long insert(NewReceipt r, long now) {
        return jdbc.sql("""
                INSERT INTO rcp_receipt (merchant_id, merchant_name, purchased_at, purchased_date, currency,
                       total_minor, tax_minor, discount_minor, payment_method, note, source, image_path, item_count,
                       created_at, updated_at)
                VALUES (:mid, :mname, :at, :date, :cur, :total, :tax, :disc, :pay, :note, :src, :img, :cnt, :now, :now)
                RETURNING id""")
                .params(params(r)).param("now", now).query(Long.class).single();
    }

    void update(long id, NewReceipt r, long now) {
        jdbc.sql("""
                UPDATE rcp_receipt SET merchant_id = :mid, merchant_name = :mname, purchased_at = :at,
                       purchased_date = :date, currency = :cur, total_minor = :total, tax_minor = :tax,
                       discount_minor = :disc, payment_method = :pay, note = :note, source = :src,
                       image_path = :img, item_count = :cnt, updated_at = :now
                WHERE id = :id""")
                .params(params(r)).param("id", id).param("now", now).update();
    }

    void replaceItems(long receiptId, List<NewItem> items) {
        jdbc.sql("DELETE FROM rcp_receipt_item WHERE receipt_id = :id").param("id", receiptId).update();
        int line = 1;
        for (NewItem it : items) {
            jdbc.sql("""
                    INSERT INTO rcp_receipt_item (receipt_id, line_no, product_id, product_name, quantity,
                           unit_price_minor, discount_minor, amount_minor, note)
                    VALUES (:rid, :line, :pid, :pname, :qty, :unit, :disc, :amt, :note)""")
                    .param("rid", receiptId).param("line", line++).param("pid", it.productId())
                    .param("pname", it.productName()).param("qty", it.quantity()).param("unit", it.unitPriceMinor())
                    .param("disc", it.discountMinor()).param("amt", it.amountMinor()).param("note", it.note())
                    .update();
        }
    }

    boolean delete(long id) {
        return jdbc.sql("DELETE FROM rcp_receipt WHERE id = :id").param("id", id).update() > 0;
    }

    void renameMerchant(long merchantId, String name) {
        jdbc.sql("UPDATE rcp_receipt SET merchant_name = :n WHERE merchant_id = :id AND merchant_name <> :n")
                .param("n", name).param("id", merchantId).update();
    }

    void renameProduct(long productId, String name) {
        jdbc.sql("UPDATE rcp_receipt_item SET product_name = :n WHERE product_id = :id AND product_name <> :n")
                .param("n", name).param("id", productId).update();
    }

    record Filter(String from, String to, Long merchantId, String like) {

        Map<String, Object> params() {
            Map<String, Object> m = new HashMap<>();
            if (from != null) {
                m.put("from", from);
            }
            if (to != null) {
                m.put("to", to);
            }
            if (merchantId != null) {
                m.put("mid", merchantId);
            }
            if (like != null) {
                m.put("q", like);
            }
            return m;
        }

        String where() {
            List<String> c = new ArrayList<>();
            if (from != null) {
                c.add("purchased_date >= :from");
            }
            if (to != null) {
                c.add("purchased_date <= :to");
            }
            if (merchantId != null) {
                c.add("merchant_id = :mid");
            }
            if (like != null) {
                c.add("(merchant_name LIKE :q ESCAPE '\\' OR EXISTS (SELECT 1 FROM rcp_receipt_item i "
                        + "WHERE i.receipt_id = rcp_receipt.id AND i.product_name LIKE :q ESCAPE '\\'))");
            }
            return c.isEmpty() ? "" : " WHERE " + String.join(" AND ", c);
        }
    }

    List<Row> search(Filter f, int offset, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM rcp_receipt" + f.where()
                        + " ORDER BY purchased_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(f.params()).param("limit", limit).param("offset", offset).query(ROW).list();
    }

    long count(Filter f) {
        return jdbc.sql("SELECT COUNT(*) FROM rcp_receipt" + f.where()).params(f.params()).query(Long.class).single();
    }

    /** First {@code perReceipt} product names of each receipt, in line order. */
    Map<Long, List<String>> firstItemNames(Collection<Long> receiptIds, int perReceipt) {
        Map<Long, List<String>> result = new HashMap<>();
        if (receiptIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT receipt_id, product_name FROM rcp_receipt_item WHERE receipt_id IN (:ids) "
                        + "ORDER BY receipt_id, line_no")
                .param("ids", receiptIds).query((rs, i) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                .forEach(e -> {
                    var names = result.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                    if (names.size() < perReceipt) {
                        names.add(e.getValue());
                    }
                });
        return result;
    }

    Optional<ReceiptFact> findFact(long id) {
        return findById(id).map(r -> toFact(r, items(id).stream().map(ReceiptRepository::toItemFact).toList()));
    }

    void forEachFact(Consumer<ReceiptFact> consumer) {
        long last = 0;
        while (true) {
            List<Row> batch = jdbc.sql("SELECT " + COLUMNS + " FROM rcp_receipt WHERE id > :last ORDER BY id LIMIT :n")
                    .param("last", last).param("n", FACT_BATCH).query(ROW).list();
            if (batch.isEmpty()) {
                return;
            }
            Map<Long, List<ReceiptItemFact>> items = new LinkedHashMap<>();
            jdbc.sql("SELECT * FROM rcp_receipt_item WHERE receipt_id IN (:ids) ORDER BY receipt_id, line_no")
                    .param("ids", batch.stream().map(Row::id).toList()).query(ITEM).list()
                    .forEach(it -> items.computeIfAbsent(it.receiptId(), k -> new ArrayList<>()).add(toItemFact(it)));
            batch.forEach(r -> consumer.accept(toFact(r, items.getOrDefault(r.id(), List.of()))));
            last = batch.get(batch.size() - 1).id();
        }
    }

    static ReceiptFact toFact(Row r, List<ReceiptItemFact> items) {
        return new ReceiptFact(r.id(), r.merchantId(), r.purchasedAt(), r.purchasedDate(), r.currency(),
                r.totalMinor(), r.discountMinor(), items);
    }

    static ReceiptItemFact toItemFact(ItemRow it) {
        return new ReceiptItemFact(it.productId(), it.quantity(), it.unitPriceMinor(), it.discountMinor(),
                it.amountMinor());
    }

    private static Map<String, Object> params(NewReceipt r) {
        Map<String, Object> m = new HashMap<>();
        m.put("mid", r.merchantId());
        m.put("mname", r.merchantName());
        m.put("at", r.purchasedAt());
        m.put("date", r.purchasedDate());
        m.put("cur", r.currency());
        m.put("total", r.totalMinor());
        m.put("tax", r.taxMinor());
        m.put("disc", r.discountMinor());
        m.put("pay", r.paymentMethod());
        m.put("note", r.note());
        m.put("src", r.source());
        m.put("img", r.imagePath());
        m.put("cnt", r.itemCount());
        return m;
    }
}
