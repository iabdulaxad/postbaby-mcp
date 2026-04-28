package echo.mcp.provider;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PostgresProvider using H2 in PostgreSQL compatibility mode.
 * The provider's connection factory is overridden to return an H2 connection.
 */
class PostgresProviderTest {

    private static final String JDBC_URL =
        "jdbc:h2:mem:pgtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private Connection sharedConn;
    private PostgresProvider provider;
    private final ConnectionParams params =
        new ConnectionParams(null, null, null, "ignored", 5432, "ignored", "sa", "");

    @BeforeEach
    void setUp() throws SQLException {
        sharedConn = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(200))");
        }

        provider = new PostgresProvider() {
            @Override
            protected Connection open(ConnectionParams p) throws SQLException {
                return DriverManager.getConnection(JDBC_URL, "sa", "");
            }

            @Override
            protected String wrapInsertReturning(String insertSql) {
                return "SELECT * FROM FINAL TABLE (" + insertSql + ")";
            }
        };
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = sharedConn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        sharedConn.close();
    }

    @Test
    void supports_onlyPostgresParams() {
        assertTrue(provider.supports(params));
        assertFalse(provider.supports(
            new ConnectionParams("app", null, null, null, null, null, null, null)));
    }

    @Test
    void listTables_returnsCreatedTable() throws Exception {
        List<TableInfo> tables = provider.listTables(params);
        assertTrue(tables.stream().anyMatch(t -> "users".equalsIgnoreCase(t.name())));
    }

    @Test
    void getTableSchema_includesColumnsAndPrimaryKey() throws Exception {
        TableInfo info = provider.getTableSchema(params, "users");
        assertEquals("users", info.name());
        assertEquals(3, info.columns().size());
        TableInfo.ColumnInfo idCol = info.columns().stream()
            .filter(c -> "id".equalsIgnoreCase(c.name()))
            .findFirst().orElseThrow();
        assertTrue(idCol.primaryKey());
    }

    @Test
    void insert_returnsRowAndSelectFindsIt() throws Exception {
        Map<String, Object> inserted = provider.insert(params, "users",
            Map.of("name", "Alice", "email", "a@example.com"));
        assertEquals("Alice", inserted.get("name"));
        assertNotNull(inserted.get("id"));

        List<Map<String, Object>> rows = provider.select(params, "users",
            null, Map.of("name", "Alice"), null, null);
        assertEquals(1, rows.size());
        assertEquals("a@example.com", rows.get(0).get("email"));
    }

    @Test
    void select_supportsColumnProjectionLimitOffset() throws Exception {
        provider.insert(params, "users", Map.of("name", "A"));
        provider.insert(params, "users", Map.of("name", "B"));
        provider.insert(params, "users", Map.of("name", "C"));

        List<Map<String, Object>> rows = provider.select(params, "users",
            List.of("name"), null, 2, 1);
        assertEquals(2, rows.size());
        rows.forEach(r -> {
            assertTrue(r.containsKey("name"));
            assertFalse(r.containsKey("email"));
        });
    }

    @Test
    void select_handlesNullEqualityAsIsNull() throws Exception {
        provider.insert(params, "users", Map.of("name", "X"));
        java.util.HashMap<String, Object> withNullEmail = new java.util.HashMap<>();
        withNullEmail.put("email", null);

        List<Map<String, Object>> rows = provider.select(params, "users",
            null, withNullEmail, null, null);
        assertEquals(1, rows.size());
        assertEquals("X", rows.get(0).get("name"));
    }

    @Test
    void update_affectsMatchingRows() throws Exception {
        provider.insert(params, "users", Map.of("name", "A", "email", "a@x.com"));
        provider.insert(params, "users", Map.of("name", "A", "email", "a2@x.com"));
        provider.insert(params, "users", Map.of("name", "B", "email", "b@x.com"));

        Map<String, Object> result = provider.update(params, "users",
            Map.of("email", "new@x.com"), Map.of("name", "A"));
        assertEquals(2, result.get("affected"));
    }

    @Test
    void update_withEmptyValuesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.update(params, "users", Map.of(), Map.of("id", 1)));
    }

    @Test
    void insert_withEmptyValuesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.insert(params, "users", Map.of()));
    }

    @Test
    void delete_requiresWhereClause() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.delete(params, "users", Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> provider.delete(params, "users", null));
    }

    @Test
    void delete_removesMatchingRows() throws Exception {
        provider.insert(params, "users", Map.of("name", "ToDelete"));
        Map<String, Object> result = provider.delete(params, "users",
            Map.of("name", "ToDelete"));
        assertEquals(1, result.get("affected"));
        assertTrue(provider.select(params, "users", null,
            Map.of("name", "ToDelete"), null, null).isEmpty());
    }

    @Test
    void identifierWithQuoteIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.select(params, "us\"ers", null, null, null, null));
    }
}
