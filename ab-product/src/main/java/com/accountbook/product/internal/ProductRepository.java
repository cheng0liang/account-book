package com.accountbook.product.internal;

import static com.accountbook.common.db.Rows.nullableDouble;

import com.accountbook.product.api.ProductView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProductRepository {

    private static final String COLUMNS = "p.id, p.name, p.brand, p.category, p.spec_text, p.spec_value, p.spec_unit, "
            + "p.spec_dim, p.spec_base_qty, p.barcode, p.created_at";

    private static final RowMapper<ProductView> MAPPER = (rs, i) -> new ProductView(
            rs.getLong("id"), rs.getString("name"), rs.getString("brand"), rs.getString("category"),
            rs.getString("spec_text"), nullableDouble(rs, "spec_value"), rs.getString("spec_unit"),
            rs.getString("spec_dim"), nullableDouble(rs, "spec_base_qty"), rs.getString("barcode"),
            List.of(), rs.getLong("created_at"));

    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ProductView> findById(long id) {
        return withTags(jdbc.sql("SELECT " + COLUMNS + " FROM prd_product p WHERE p.id = :id")
                .param("id", id).query(MAPPER).list()).stream().findFirst();
    }

    Map<Long, ProductView> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductView> result = new LinkedHashMap<>();
        withTags(jdbc.sql("SELECT " + COLUMNS + " FROM prd_product p WHERE p.id IN (:ids)")
                .param("ids", ids).query(MAPPER).list()).forEach(p -> result.put(p.id(), p));
        return result;
    }

    Optional<ProductView> findByKey(String normalizedName, String brandKey, String specKey) {
        return withTags(jdbc.sql("SELECT " + COLUMNS + " FROM prd_product p "
                        + "WHERE p.normalized_name = :n AND p.brand_key = :b AND p.spec_key = :s")
                .param("n", normalizedName).param("b", brandKey).param("s", specKey)
                .query(MAPPER).list()).stream().findFirst();
    }

    long insert(Row r, long now) {
        return jdbc.sql("""
                INSERT INTO prd_product (name, normalized_name, brand, brand_key, category, spec_text, spec_key,
                                         spec_value, spec_unit, spec_dim, spec_base_qty, barcode, created_at, updated_at)
                VALUES (:name, :nn, :brand, :bk, :category, :st, :sk, :sv, :su, :sd, :sb, :barcode, :now, :now)
                RETURNING id""")
                .params(rowParams(r)).param("now", now).query(Long.class).single();
    }

    void update(long id, Row r, long now) {
        jdbc.sql("""
                UPDATE prd_product SET name = :name, normalized_name = :nn, brand = :brand, brand_key = :bk,
                       category = :category, spec_text = :st, spec_key = :sk, spec_value = :sv, spec_unit = :su,
                       spec_dim = :sd, spec_base_qty = :sb, barcode = :barcode, updated_at = :now
                WHERE id = :id""")
                .params(rowParams(r)).param("id", id).param("now", now).update();
    }

    void replaceTags(long productId, List<String> tags) {
        jdbc.sql("DELETE FROM prd_product_tag WHERE product_id = :id").param("id", productId).update();
        for (String tag : tags) {
            jdbc.sql("INSERT INTO prd_product_tag (product_id, tag) VALUES (:id, :tag)")
                    .param("id", productId).param("tag", tag).update();
        }
    }

    List<ProductView> search(Filter f, int offset, int limit) {
        var w = f.where();
        return withTags(jdbc.sql("SELECT " + COLUMNS + " FROM prd_product p" + w.sql()
                        + " ORDER BY p.updated_at DESC, p.id DESC LIMIT :limit OFFSET :offset")
                .params(w.params).param("limit", limit).param("offset", offset).query(MAPPER).list());
    }

    long count(Filter f) {
        var w = f.where();
        return jdbc.sql("SELECT COUNT(*) FROM prd_product p" + w.sql()).params(w.params).query(Long.class).single();
    }

    List<ProductView> match(String normalizedName, int limit) {
        return withTags(jdbc.sql("SELECT " + COLUMNS + """
                 FROM prd_product p
                WHERE p.normalized_name = :n OR p.normalized_name LIKE '%' || :n || '%'
                   OR :n LIKE '%' || p.normalized_name || '%'
                ORDER BY (p.normalized_name = :n) DESC, ABS(LENGTH(p.normalized_name) - LENGTH(:n)), p.id
                LIMIT :limit""")
                .param("n", normalizedName).param("limit", limit).query(MAPPER).list());
    }

    List<String> brands(String like, int limit) {
        return jdbc.sql("SELECT brand FROM prd_product WHERE brand IS NOT NULL AND brand LIKE :q ESCAPE '\\' "
                        + "GROUP BY brand ORDER BY COUNT(*) DESC, brand LIMIT :limit")
                .param("q", like).param("limit", limit).query(String.class).list();
    }

    List<String> tags(String like, int limit) {
        return jdbc.sql("SELECT tag FROM prd_product_tag WHERE tag LIKE :q ESCAPE '\\' "
                        + "GROUP BY tag ORDER BY COUNT(*) DESC, tag LIMIT :limit")
                .param("q", like).param("limit", limit).query(String.class).list();
    }

    private List<ProductView> withTags(List<ProductView> products) {
        if (products.isEmpty()) {
            return products;
        }
        Map<Long, List<String>> tags = new HashMap<>();
        jdbc.sql("SELECT product_id, tag FROM prd_product_tag WHERE product_id IN (:ids) ORDER BY tag")
                .param("ids", products.stream().map(ProductView::id).toList())
                .query((rs, i) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                .forEach(e -> tags.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return products.stream().map(p -> new ProductView(p.id(), p.name(), p.brand(), p.category(), p.specText(),
                p.specValue(), p.specUnit(), p.specDim(), p.specBaseQty(), p.barcode(),
                tags.getOrDefault(p.id(), List.of()), p.createdAt())).toList();
    }

    private static Map<String, Object> rowParams(Row r) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", r.name());
        m.put("nn", r.normalizedName());
        m.put("brand", r.brand());
        m.put("bk", r.brandKey());
        m.put("category", r.category());
        m.put("st", r.specText());
        m.put("sk", r.specKey());
        m.put("sv", r.specValue());
        m.put("su", r.specUnit());
        m.put("sd", r.specDim());
        m.put("sb", r.specBaseQty());
        m.put("barcode", r.barcode());
        return m;
    }

    record Row(String name, String normalizedName, String brand, String brandKey, String category, String specText,
               String specKey, Double specValue, String specUnit, String specDim, Double specBaseQty, String barcode,
               List<String> tags) {
    }

    record Filter(String normalizedQuery, String brand, String category, String tag) {

        Where where() {
            var w = new Where();
            if (normalizedQuery != null && !normalizedQuery.isEmpty()) {
                w.add("p.normalized_name LIKE '%' || :q || '%'", "q", normalizedQuery);
            }
            if (brand != null) {
                w.add("p.brand = :brand", "brand", brand);
            }
            if (category != null) {
                w.add("p.category = :category", "category", category);
            }
            if (tag != null) {
                w.add("EXISTS (SELECT 1 FROM prd_product_tag t WHERE t.product_id = p.id AND t.tag = :tag)", "tag", tag);
            }
            return w;
        }
    }

    static final class Where {
        private final List<String> clauses = new ArrayList<>();
        final Map<String, Object> params = new HashMap<>();

        void add(String clause, String name, Object value) {
            clauses.add(clause);
            params.put(name, value);
        }

        String sql() {
            return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        }
    }
}
