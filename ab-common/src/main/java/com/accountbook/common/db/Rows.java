package com.accountbook.common.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Null-safe column readers; SQLite's driver returns Integer/Long/Double depending on the stored value. */
public final class Rows {

    private Rows() {
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    public static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}
