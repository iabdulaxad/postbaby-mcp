package echo.mcp.provider;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import echo.mcp.model.TableInfo.ColumnInfo;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Direct JDBC access. Identifiers are quoted; values are bound via PreparedStatement.
 * Values are coerced into JSON-friendly forms so Jackson can serialize them.
 */
@Component
public class PostgresProvider implements DatabaseProvider {

    @Override
    public boolean supports(ConnectionParams params) {
        return params.isPostgres();
    }

    private Connection open(ConnectionParams p) throws SQLException {
        String url = "jdbc:postgresql://" + p.pgHost() + ":"
            + (p.pgPort() == null ? 5432 : p.pgPort()) + "/" + p.pgDatabase();
        return DriverManager.getConnection(url, p.pgUser(), p.pgPassword());
    }

    @Override
    public List<TableInfo> listTables(ConnectionParams params) throws Exception {
        List<TableInfo> out = new ArrayList<>();
        try (Connection c = open(params)) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(null, "public", "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    out.add(new TableInfo(
                        rs.getString("TABLE_NAME"),
                        rs.getString("TABLE_SCHEM"),
                        List.of()
                    ));
                }
            }
        }
        return out;
    }

    @Override
    public TableInfo getTableSchema(ConnectionParams params, String table) throws Exception {
        try (Connection c = open(params)) {
            DatabaseMetaData md = c.getMetaData();
            Set<String> pks = new HashSet<>();
            try (ResultSet rs = md.getPrimaryKeys(null, "public", table)) {
                while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
            }
            List<ColumnInfo> cols = new ArrayList<>();
            try (ResultSet rs = md.getColumns(null, "public", table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    cols.add(new ColumnInfo(
                        name,
                        rs.getString("TYPE_NAME"),
                        "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                        pks.contains(name),
                        rs.getString("COLUMN_DEF")
                    ));
                }
            }
            return new TableInfo(table, "public", cols);
        }
    }

    @Override
    public List<Map<String, Object>> select(
            ConnectionParams params, String table, List<String> columns,
            Map<String, Object> where, Integer limit, Integer offset) throws Exception {

        String cols = (columns == null || columns.isEmpty())
            ? "*"
            : columns.stream().map(PostgresProvider::ident).reduce((a, b) -> a + "," + b).orElse("*");

        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
            .append(" FROM ").append(ident(table));

        List<Object> binds = new ArrayList<>();
        appendWhere(sql, where, binds);
        if (limit != null) sql.append(" LIMIT ").append(limit.intValue());
        if (offset != null) sql.append(" OFFSET ").append(offset.intValue());

        try (Connection c = open(params); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, binds);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    @Override
    public Map<String, Object> insert(
            ConnectionParams params, String table, Map<String, Object> values) throws Exception {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("insert requires non-empty values");
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(ident(table)).append(" (");
        StringBuilder placeholders = new StringBuilder();
        List<Object> binds = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) { sql.append(','); placeholders.append(','); }
            sql.append(ident(e.getKey()));
            placeholders.append('?');
            binds.add(e.getValue());
            first = false;
        }
        sql.append(") VALUES (").append(placeholders).append(") RETURNING *");

        try (Connection c = open(params); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, binds);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = readAll(rs);
                return rows.isEmpty() ? Map.of() : rows.get(0);
            }
        }
    }

    @Override
    public Map<String, Object> update(
            ConnectionParams params, String table,
            Map<String, Object> values, Map<String, Object> where) throws Exception {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("update requires non-empty values");
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(ident(table)).append(" SET ");
        List<Object> binds = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (!first) sql.append(',');
            sql.append(ident(e.getKey())).append("=?");
            binds.add(e.getValue());
            first = false;
        }
        appendWhere(sql, where, binds);

        try (Connection c = open(params); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, binds);
            int n = ps.executeUpdate();
            return Map.of("affected", n);
        }
    }

    @Override
    public Map<String, Object> delete(
            ConnectionParams params, String table, Map<String, Object> where) throws Exception {
        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException(
                "delete requires a non-empty where clause to prevent accidental full-table deletion");
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(ident(table));
        List<Object> binds = new ArrayList<>();
        appendWhere(sql, where, binds);

        try (Connection c = open(params); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, binds);
            int n = ps.executeUpdate();
            return Map.of("affected", n);
        }
    }

    // ---------- helpers ----------

    private static void appendWhere(StringBuilder sql, Map<String, Object> where, List<Object> binds) {
        if (where == null || where.isEmpty()) return;
        sql.append(" WHERE ");
        boolean first = true;
        for (Map.Entry<String, Object> e : where.entrySet()) {
            if (!first) sql.append(" AND ");
            if (e.getValue() == null) {
                sql.append(ident(e.getKey())).append(" IS NULL");
            } else {
                sql.append(ident(e.getKey())).append("=?");
                binds.add(e.getValue());
            }
            first = false;
        }
    }

    private static void bind(PreparedStatement ps, List<Object> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) {
            ps.setObject(i + 1, binds.get(i));
        }
    }

    private static List<Map<String, Object>> readAll(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                row.put(md.getColumnLabel(i), toJsonFriendly(rs.getObject(i)));
            }
            rows.add(row);
        }
        return rows;
    }

    private static Object toJsonFriendly(Object v) throws SQLException {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof java.sql.Time t) return t.toLocalTime().toString();
        if (v instanceof java.sql.Array arr) {
            Object raw = arr.getArray();
            if (raw instanceof Object[] oa) {
                List<Object> out = new ArrayList<>(oa.length);
                for (Object e : oa) out.add(toJsonFriendly(e));
                return out;
            }
            return raw;
        }
        if (v instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        String cls = v.getClass().getName();
        if (cls.startsWith("org.postgresql.util.") || cls.equals("java.util.UUID")) {
            return v.toString();
        }
        return v;
    }

    private static String ident(String raw) {
        if (raw == null || raw.isEmpty() || raw.contains("\"")) {
            throw new IllegalArgumentException("Invalid identifier: " + raw);
        }
        return "\"" + raw + "\"";
    }
}
