package echo.mcp.provider;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UCodeProviderTest {

    private MockWebServer server;
    private UCodeProvider provider;
    private final ConnectionParams params =
        new ConnectionParams("app-key-123", "env-1", "proj-1", null, null, null, null, null);

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        // strip trailing slash so concatenation matches the provider's "+/v2/items/..." pattern
        String url = server.url("").toString();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        System.setProperty("ucode.base-url", url);
        provider = new UCodeProvider();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("ucode.base-url");
        server.shutdown();
    }

    @Test
    void supports_onlyUCodeParams() {
        assertTrue(provider.supports(params));
        assertFalse(provider.supports(
            new ConnectionParams(null, null, null, "h", 5432, "db", "u", "pw")));
    }

    @Test
    void listTables_alwaysThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
            () -> provider.listTables(params));
    }

    @Test
    void select_buildsExpectedRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"data\":{\"response\":[{\"guid\":\"g1\",\"name\":\"A\"}]}}}"));

        List<Map<String, Object>> rows = provider.select(params, "people",
            List.of("guid", "name"), Map.of("name", "A"), 5, 0);

        assertEquals(1, rows.size());
        assertEquals("g1", rows.get(0).get("guid"));

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().startsWith("/v2/items/people"));
        assertTrue(req.getPath().contains("from-ofs=true"));
        assertTrue(req.getPath().contains("limit=5"));
        assertTrue(req.getPath().contains("offset=0"));
        assertEquals("API-KEY", req.getHeader("authorization"));
        assertEquals("app-key-123", req.getHeader("X-API-KEY"));
        assertEquals("env-1", req.getHeader("environment-id"));
        assertEquals("proj-1", req.getHeader("project-id"));
    }

    @Test
    void select_returnsEmptyListWhenResponseShapeMissing() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"unrelated\":true}"));

        List<Map<String, Object>> rows = provider.select(params, "x",
            null, null, null, null);
        assertTrue(rows.isEmpty());
    }

    @Test
    void getTableSchema_inferTypesFromSampleRow() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"data\":{\"response\":[{\"guid\":\"g1\",\"age\":42,\"active\":true,\"tags\":[\"a\"],\"meta\":{}}]}}}"));

        TableInfo info = provider.getTableSchema(params, "people");
        assertEquals("people", info.name());
        Map<String, TableInfo.ColumnInfo> byName = new java.util.HashMap<>();
        info.columns().forEach(c -> byName.put(c.name(), c));

        assertEquals("string", byName.get("guid").type());
        assertTrue(byName.get("guid").primaryKey());
        assertEquals("integer", byName.get("age").type());
        assertEquals("boolean", byName.get("active").type());
        assertEquals("array", byName.get("tags").type());
        assertEquals("object", byName.get("meta").type());
    }

    @Test
    void insert_postsAndReturnsCreated() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"data\":{\"guid\":\"g1\",\"name\":\"A\"}}}"));

        Map<String, Object> created = provider.insert(params, "people", Map.of("name", "A"));
        assertEquals("g1", created.get("guid"));

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().startsWith("/v2/items/people"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"data\""));
        assertTrue(body.contains("\"name\":\"A\""));
    }

    @Test
    void update_includesGuidAndIsPut() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"data\":{\"data\":{\"guid\":\"g1\",\"name\":\"B\"}}}"));

        Map<String, Object> updated = provider.update(params, "people",
            Map.of("name", "B"), Map.of("guid", "g1"));
        assertEquals("B", updated.get("name"));

        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"guid\":\"g1\""));
        assertTrue(body.contains("\"name\":\"B\""));
    }

    @Test
    void delete_requiresGuid() {
        assertThrows(IllegalArgumentException.class,
            () -> provider.delete(params, "people", Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> provider.delete(params, "people", null));
    }

    @Test
    void delete_targetsGuidUrl() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"done\"}"));

        Map<String, Object> result = provider.delete(params, "people", Map.of("guid", "g1"));
        assertEquals("done", result.get("status"));

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertTrue(req.getPath().startsWith("/v2/items/people/g1"));
    }
}
