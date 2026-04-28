package echo.mcp.tools;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import echo.mcp.provider.DatabaseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DatabaseToolsTest {

    private DatabaseProvider postgres;
    private DatabaseProvider ucode;
    private DatabaseTools tools;

    @BeforeEach
    void setUp() {
        postgres = mock(DatabaseProvider.class);
        ucode = mock(DatabaseProvider.class);
        when(postgres.supports(any())).thenAnswer(inv -> ((ConnectionParams) inv.getArgument(0)).isPostgres());
        when(ucode.supports(any())).thenAnswer(inv -> ((ConnectionParams) inv.getArgument(0)).isUCode());
        tools = new DatabaseTools(List.of(postgres, ucode));
    }

    private ConnectionParams pgParams() {
        return new ConnectionParams(null, null, null, "h", 5432, "db", "u", "pw");
    }

    private ConnectionParams ucodeParams() {
        return new ConnectionParams("app", null, null, null, null, null, null, null);
    }

    @Test
    void listTables_routesToPostgresProvider() throws Exception {
        ConnectionParams params = pgParams();
        TableInfo expected = new TableInfo("t", "public", List.of());
        when(postgres.listTables(params)).thenReturn(List.of(expected));

        List<TableInfo> result = tools.listTables(params);

        assertEquals(1, result.size());
        assertEquals("t", result.get(0).name());
        verify(postgres).listTables(params);
        verifyNoInteractions(ucode);
    }

    @Test
    void listTables_routesToUCodeProvider() throws Exception {
        ConnectionParams params = ucodeParams();
        when(ucode.listTables(params)).thenThrow(new UnsupportedOperationException("nope"));

        UnsupportedOperationException ex = assertThrows(
            UnsupportedOperationException.class,
            () -> tools.listTables(params));
        assertEquals("nope", ex.getMessage());
        verify(ucode).listTables(params);
        verify(postgres, never()).listTables(any());
    }

    @Test
    void resolve_throwsWhenNoProviderMatches() {
        ConnectionParams empty = new ConnectionParams(null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> tools.listTables(empty));
    }

    @Test
    void select_passesAllArgumentsThrough() throws Exception {
        ConnectionParams params = pgParams();
        List<String> cols = List.of("id", "name");
        Map<String, Object> where = Map.of("id", 1);
        when(postgres.select(params, "users", cols, where, 10, 0))
            .thenReturn(List.of(Map.of("id", 1, "name", "x")));

        List<Map<String, Object>> rows = tools.select(params, "users", cols, where, 10, 0);

        assertEquals(1, rows.size());
        verify(postgres).select(params, "users", cols, where, 10, 0);
    }

    @Test
    void insert_returnsProviderResult() throws Exception {
        ConnectionParams params = pgParams();
        Map<String, Object> values = Map.of("name", "abc");
        when(postgres.insert(eq(params), eq("users"), eq(values))).thenReturn(Map.of("id", 7, "name", "abc"));

        Map<String, Object> result = tools.insert(params, "users", values);

        assertEquals(7, result.get("id"));
    }

    @Test
    void update_routesToCorrectProvider() throws Exception {
        ConnectionParams params = ucodeParams();
        Map<String, Object> values = Map.of("name", "x");
        Map<String, Object> where = Map.of("guid", "g-1");
        when(ucode.update(params, "items", values, where)).thenReturn(Map.of("guid", "g-1"));

        Map<String, Object> result = tools.update(params, "items", values, where);

        assertEquals("g-1", result.get("guid"));
    }

    @Test
    void delete_routesToCorrectProvider() throws Exception {
        ConnectionParams params = pgParams();
        Map<String, Object> where = Map.of("id", 1);
        when(postgres.delete(params, "users", where)).thenReturn(Map.of("affected", 1));

        Map<String, Object> result = tools.delete(params, "users", where);

        assertEquals(1, result.get("affected"));
    }
}
