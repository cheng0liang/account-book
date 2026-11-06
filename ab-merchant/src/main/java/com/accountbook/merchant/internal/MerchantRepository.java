package com.accountbook.merchant.internal;

import static com.accountbook.common.db.Rows.nullableDouble;

import com.accountbook.merchant.api.MerchantView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MerchantRepository {

    private static final String COLUMNS =
            "id, name, country, city, district, type, address, latitude, longitude, created_at";

    private static final RowMapper<MerchantView> MAPPER = (rs, i) -> new MerchantView(
            rs.getLong("id"), rs.getString("name"), rs.getString("country"), rs.getString("city"),
            rs.getString("district"), rs.getString("type"), rs.getString("address"),
            nullableDouble(rs, "latitude"), nullableDouble(rs, "longitude"), rs.getLong("created_at"));

    private final JdbcClient jdbc;

    MerchantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<MerchantView> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM mer_merchant WHERE id = :id")
                .param("id", id).query(MAPPER).optional();
    }

    Optional<MerchantView> findByKey(String normalizedName, String cityKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM mer_merchant WHERE normalized_name = :n AND city_key = :c")
                .param("n", normalizedName).param("c", cityKey).query(MAPPER).optional();
    }

    long insert(Row r, long now) {
        return jdbc.sql("""
                INSERT INTO mer_merchant (name, normalized_name, country, city, city_key, district, type, address,
                                          latitude, longitude, created_at, updated_at)
                VALUES (:name, :nn, :country, :city, :ck, :district, :type, :address, :lat, :lng, :now, :now)
                RETURNING id""")
                .param("name", r.name()).param("nn", r.normalizedName()).param("country", r.country())
                .param("city", r.city()).param("ck", r.cityKey()).param("district", r.district())
                .param("type", r.type()).param("address", r.address())
                .param("lat", r.latitude()).param("lng", r.longitude()).param("now", now)
                .query(Long.class).single();
    }

    void update(long id, Row r, long now) {
        jdbc.sql("""
                UPDATE mer_merchant SET name = :name, normalized_name = :nn, country = :country, city = :city,
                       city_key = :ck, district = :district, type = :type, address = :address,
                       latitude = :lat, longitude = :lng, updated_at = :now
                WHERE id = :id""")
                .param("id", id).param("name", r.name()).param("nn", r.normalizedName())
                .param("country", r.country()).param("city", r.city()).param("ck", r.cityKey())
                .param("district", r.district()).param("type", r.type()).param("address", r.address())
                .param("lat", r.latitude()).param("lng", r.longitude()).param("now", now)
                .update();
    }

    List<MerchantView> search(Filter f, int offset, int limit) {
        var where = new Where();
        f.apply(where);
        return jdbc.sql("SELECT " + COLUMNS + " FROM mer_merchant" + where.sql()
                        + " ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(where.params).param("limit", limit).param("offset", offset)
                .query(MAPPER).list();
    }

    long count(Filter f) {
        var where = new Where();
        f.apply(where);
        return jdbc.sql("SELECT COUNT(*) FROM mer_merchant" + where.sql())
                .params(where.params).query(Long.class).single();
    }

    List<MerchantView> match(String normalizedName, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM mer_merchant
                WHERE normalized_name = :n OR normalized_name LIKE '%' || :n || '%'
                   OR :n LIKE '%' || normalized_name || '%'
                ORDER BY (normalized_name = :n) DESC, ABS(LENGTH(normalized_name) - LENGTH(:n)), id
                LIMIT :limit""")
                .param("n", normalizedName).param("limit", limit).query(MAPPER).list();
    }

    record Row(String name, String normalizedName, String country, String city, String cityKey, String district,
               String type, String address, Double latitude, Double longitude) {
    }

    record Filter(String normalizedQuery, String country, String city, String district, String type) {

        void apply(Where w) {
            if (normalizedQuery != null && !normalizedQuery.isEmpty()) {
                w.add("normalized_name LIKE '%' || :q || '%'", "q", normalizedQuery);
            }
            if (country != null) {
                w.add("country = :country", "country", country);
            }
            if (city != null) {
                w.add("city = :city", "city", city);
            }
            if (district != null) {
                w.add("district = :district", "district", district);
            }
            if (type != null) {
                w.add("type = :type", "type", type);
            }
        }
    }

    private static final class Where {
        private final List<String> clauses = new ArrayList<>();
        private final java.util.Map<String, Object> params = new java.util.HashMap<>();

        void add(String clause, String name, Object value) {
            clauses.add(clause);
            params.put(name, value);
        }

        String sql() {
            return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        }
    }
}
