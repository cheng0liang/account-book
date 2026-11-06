package com.accountbook.analytics.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * All chart SQL. Reads only the daily rollup joined to two tiny dimension tables. Every identifier interpolated into
 * SQL comes from a fixed whitelist in this class, never from request input; values are always bound parameters.
 */
@Repository
class QueryRepository {

    static final String FROM = " FROM ana_price_daily d JOIN ana_merchant_dim m ON m.id = d.merchant_id"
            + " JOIN ana_product_dim p ON p.id = d.product_id";

    private static final String NORMALIZE =
            "(CASE WHEN p.spec_base_qty > 0 THEN (CASE p.spec_dim WHEN 'COUNT' THEN 1.0 ELSE 100.0 END) / p.spec_base_qty ELSE 1.0 END)";

    /** Per-request SQL context: the filter, whether prices are normalised, and the single unit dimension in use. */
    record Ctx(Filter filter, boolean normalized, String dim) {

        String k() {
            return normalized ? NORMALIZE : "1.0";
        }

        String unit() {
            if (!normalized || dim == null) {
                return "item";
            }
            return switch (dim) {
                case "WEIGHT" -> "100g";
                case "VOLUME" -> "100ml";
                default -> "item";
            };
        }

        boolean empty() {
            return normalized && dim == null;
        }
    }

    record TrendRow(String bucket, long merchantId, String merchantName, long n, double avg, double min, double max) {
    }

    record GroupRow(Object id, String label, long n, double avg, double min, double max) {
    }

    record SpendRow(String id, String label, long amountMinor, long n) {
    }

    record TableRow(long merchantId, String name, String city, String district, long n, double avg, double min,
                    double max) {
    }

    record Latest(long merchantId, String date, double price) {
    }

    private final JdbcClient jdbc;

    QueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final class Where {
        final List<String> clauses = new ArrayList<>();
        final Map<String, Object> params = new HashMap<>();

        void add(String clause, String name, Object value) {
            clauses.add(clause);
            params.put(name, value);
        }

        String sql() {
            return " WHERE " + String.join(" AND ", clauses);
        }
    }

    private static Where where(Ctx c) {
        Filter f = c.filter();
        Where w = new Where();
        w.add("d.currency = :cur", "cur", f.currency());
        if (f.from() != null) {
            w.add("d.purchased_date >= :from", "from", f.from());
        }
        if (f.to() != null) {
            w.add("d.purchased_date <= :to", "to", f.to());
        }
        if (f.country() != null) {
            w.add("m.country = :country", "country", f.country());
        }
        if (f.city() != null) {
            w.add("m.city = :city", "city", f.city());
        }
        if (f.district() != null) {
            w.add("m.district = :district", "district", f.district());
        }
        if (f.merchantType() != null) {
            w.add("m.type = :mtype", "mtype", f.merchantType());
        }
        if (f.merchantId() != null) {
            w.add("d.merchant_id = :mid", "mid", f.merchantId());
        }
        if (f.productId() != null) {
            w.add("d.product_id = :pid", "pid", f.productId());
        }
        if (f.brand() != null) {
            w.add("p.brand = :brand", "brand", f.brand());
        }
        if (f.category() != null) {
            w.add("p.category = :category", "category", f.category());
        }
        if (f.tag() != null) {
            w.add("d.product_id IN (SELECT product_id FROM ana_product_tag WHERE tag = :tag)", "tag", f.tag());
        }
        if (c.normalized() && c.dim() != null) {
            w.add("p.spec_dim = :dim AND p.spec_base_qty > 0", "dim", c.dim());
        }
        return w;
    }

    Ctx context(Filter f, boolean normalized) {
        if (!normalized) {
            return new Ctx(f, false, null);
        }
        var probe = new Ctx(f, false, null);
        Where w = where(probe);
        w.clauses.add("p.spec_base_qty > 0");
        String dim = jdbc.sql("SELECT p.spec_dim" + FROM + w.sql() + " GROUP BY p.spec_dim ORDER BY SUM(d.n) DESC LIMIT 1")
                .params(w.params).query(String.class).optional().orElse(null);
        return new Ctx(f, true, dim);
    }

    static String bucketExpr(String granularity) {
        return switch (granularity) {
            case "month" -> "substr(d.purchased_date, 1, 7)";
            case "week" -> "date(d.purchased_date, '-' || ((CAST(strftime('%w', d.purchased_date) AS INTEGER) + 6) % 7) || ' days')";
            default -> "d.purchased_date";
        };
    }

    List<Long> topMerchants(Ctx c, int limit) {
        Where w = where(c);
        return jdbc.sql("SELECT d.merchant_id" + FROM + w.sql()
                        + " GROUP BY d.merchant_id ORDER BY SUM(d.n) DESC, d.merchant_id LIMIT :limit")
                .params(w.params).param("limit", limit).query(Long.class).list();
    }

    List<TrendRow> trend(Ctx c, List<Long> merchantIds, String granularity) {
        Where w = where(c);
        w.add("d.merchant_id IN (:mids)", "mids", merchantIds);
        String k = c.k();
        String bucket = bucketExpr(granularity);
        return jdbc.sql("SELECT " + bucket + " AS b, d.merchant_id, m.name, SUM(d.n), SUM(d.sum_unit * " + k
                        + ") / SUM(d.n), MIN(d.min_unit * " + k + "), MAX(d.max_unit * " + k + ")"
                        + FROM + w.sql() + " GROUP BY b, d.merchant_id ORDER BY b")
                .params(w.params)
                .query((rs, i) -> new TrendRow(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                        rs.getDouble(5), rs.getDouble(6), rs.getDouble(7))).list();
    }

    /** dimension is one of Filter.COMPARE. */
    List<GroupRow> compare(Ctx c, String dimension) {
        String[] g = switch (dimension) {
            case "merchant" -> new String[] {"d.merchant_id", "m.name"};
            case "city" -> new String[] {"m.city", "m.city"};
            case "district" -> new String[] {"m.district", "m.district"};
            case "country" -> new String[] {"m.country", "m.country"};
            case "merchantType" -> new String[] {"m.type", "m.type"};
            case "brand" -> new String[] {"p.brand", "p.brand"};
            case "product" -> new String[] {"d.product_id", "p.name"};
            default -> throw new IllegalArgumentException(dimension);
        };
        Where w = where(c);
        w.clauses.add(g[0] + " IS NOT NULL");
        String k = c.k();
        return jdbc.sql("SELECT " + g[0] + ", MAX(" + g[1] + "), SUM(d.n), SUM(d.sum_unit * " + k
                        + ") / SUM(d.n) AS av, MIN(d.min_unit * " + k + "), MAX(d.max_unit * " + k + ")"
                        + FROM + w.sql() + " GROUP BY " + g[0] + " ORDER BY av, " + g[0] + " LIMIT 30")
                .params(w.params)
                .query((rs, i) -> new GroupRow(rs.getObject(1), rs.getString(2), rs.getLong(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6))).list();
    }

    List<SpendRow> spending(Ctx c, String groupBy) {
        String[] g = switch (groupBy) {
            case "day" -> new String[] {"d.purchased_date", "d.purchased_date"};
            case "month" -> new String[] {"substr(d.purchased_date, 1, 7)", "substr(d.purchased_date, 1, 7)"};
            case "merchant" -> new String[] {"CAST(d.merchant_id AS TEXT)", "m.name"};
            case "merchantType" -> new String[] {"COALESCE(m.type, '')", "COALESCE(m.type, '')"};
            case "category" -> new String[] {"COALESCE(p.category, '')", "COALESCE(p.category, '')"};
            case "brand" -> new String[] {"COALESCE(p.brand, '')", "COALESCE(p.brand, '')"};
            case "city" -> new String[] {"COALESCE(m.city, '')", "COALESCE(m.city, '')"};
            case "district" -> new String[] {"COALESCE(m.district, '')", "COALESCE(m.district, '')"};
            default -> throw new IllegalArgumentException(groupBy);
        };
        boolean time = groupBy.equals("day") || groupBy.equals("month");
        Where w = where(c);
        return jdbc.sql("SELECT " + g[0] + " AS gid, MAX(" + g[1] + "), SUM(d.amount_minor) AS amt, SUM(d.n)"
                        + FROM + w.sql() + " GROUP BY gid ORDER BY " + (time ? "gid" : "amt DESC, gid"))
                .params(w.params)
                .query((rs, i) -> new SpendRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4))).list();
    }

    long receiptCount(Ctx c) {
        Where w = where(c);
        return jdbc.sql("SELECT COUNT(DISTINCT f.receipt_id) FROM ana_price_fact f"
                        + " JOIN ana_merchant_dim m ON m.id = f.merchant_id JOIN ana_product_dim p ON p.id = f.product_id"
                        + w.sql().replace("d.", "f.")).params(w.params).query(Long.class).single();
    }

    List<TableRow> priceTable(Ctx c) {
        Where w = where(c);
        String k = c.k();
        return jdbc.sql("SELECT d.merchant_id, m.name, m.city, m.district, SUM(d.n), SUM(d.sum_unit * " + k
                        + ") / SUM(d.n), MIN(d.min_unit * " + k + "), MAX(d.max_unit * " + k + ")"
                        + FROM + w.sql() + " GROUP BY d.merchant_id")
                .params(w.params)
                .query((rs, i) -> new TableRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8))).list();
    }

    List<Latest> latest(Ctx c) {
        Where w = where(c);
        String k = c.k();
        return jdbc.sql("SELECT merchant_id, dt, price FROM (SELECT d.merchant_id AS merchant_id, d.purchased_date AS dt,"
                        + " d.sum_unit * " + k + " / d.n AS price, ROW_NUMBER() OVER (PARTITION BY d.merchant_id"
                        + " ORDER BY d.purchased_date DESC) AS rn" + FROM + w.sql() + ") WHERE rn = 1")
                .params(w.params)
                .query((rs, i) -> new Latest(rs.getLong(1), rs.getString(2), rs.getDouble(3))).list();
    }

    Map<String, Object> facets() {
        String merchants = "(SELECT DISTINCT merchant_id FROM ana_price_daily)";
        String prods = "(SELECT DISTINCT product_id FROM ana_price_daily)";
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("currencies", strings("SELECT DISTINCT currency FROM ana_price_daily ORDER BY 1"));
        out.put("countries", strings("SELECT DISTINCT country FROM ana_merchant_dim WHERE country IS NOT NULL AND id IN "
                + merchants + " ORDER BY 1"));
        out.put("cities", jdbc.sql("SELECT DISTINCT country, city FROM ana_merchant_dim WHERE city IS NOT NULL AND id IN "
                        + merchants + " ORDER BY 1, 2")
                .query((rs, i) -> Map.of("country", nz(rs.getString(1)), "city", rs.getString(2))).list());
        out.put("districts", jdbc.sql("SELECT DISTINCT country, city, district FROM ana_merchant_dim"
                        + " WHERE district IS NOT NULL AND id IN " + merchants + " ORDER BY 1, 2, 3")
                .query((rs, i) -> Map.of("country", nz(rs.getString(1)), "city", nz(rs.getString(2)),
                        "district", rs.getString(3))).list());
        out.put("merchantTypes", strings("SELECT DISTINCT type FROM ana_merchant_dim WHERE type IS NOT NULL AND id IN "
                + merchants + " ORDER BY 1"));
        out.put("brands", strings("SELECT DISTINCT brand FROM ana_product_dim WHERE brand IS NOT NULL AND id IN "
                + prods + " ORDER BY 1"));
        out.put("categories", strings("SELECT DISTINCT category FROM ana_product_dim WHERE category IS NOT NULL AND id IN "
                + prods + " ORDER BY 1"));
        out.put("tags", strings("SELECT DISTINCT tag FROM ana_product_tag WHERE product_id IN " + prods + " ORDER BY 1"));
        out.put("dateRange", jdbc.sql("SELECT MIN(purchased_date), MAX(purchased_date) FROM ana_price_daily")
                .query((rs, i) -> {
                    Map<String, Object> r = new java.util.LinkedHashMap<>();
                    r.put("min", rs.getString(1));
                    r.put("max", rs.getString(2));
                    return r;
                }).single());
        return out;
    }

    private List<String> strings(String sql) {
        return jdbc.sql(sql).query(String.class).list();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
