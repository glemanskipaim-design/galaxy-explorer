package dev.galaxyexplorer.catalog;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Conversoes pequenas entre JDBC/PostgreSQL e tipos Java, usadas pelos repositorios. */
public final class JdbcValues {

    private JdbcValues() {
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** {@code timestamptz} como parametro: o driver PostgreSQL aceita {@link OffsetDateTime}, nao {@link Instant}. */
    public static OffsetDateTime timestamptz(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static List<Long> longList(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<Long> out = new ArrayList<>(values.length);
        for (Object v : values) {
            out.add(((Number) v).longValue());
        }
        return Collections.unmodifiableList(out);
    }

    public static List<UUID> uuidList(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<UUID> out = new ArrayList<>(values.length);
        for (Object v : values) {
            out.add((UUID) v);
        }
        return Collections.unmodifiableList(out);
    }
}
