package echo.mcp.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider backed by the u-code runtime API, mirroring the official
 * <a href="https://github.com/Ucode-io/ucode_sdk">ucode-io/ucode_sdk</a> Go client.
 *
 * <h3>Auth</h3>
 * Two headers are sent per request:
 * <pre>
 *   authorization: API-KEY
 *   X-API-KEY:     &lt;appId&gt;
 * </pre>
 * ({@code environment-id} is optional — passed through when supplied but the SDK does not require it.)
 *
 * <h3>Base URL</h3>
 * Defaults to {@code https://api.client.u-code.io}. Override via
 * {@code -Ducode.base-url=...} JVM system property if your u-code app uses a
 * custom domain (each project can have its own BaseURL).
 *
 * <h3>Endpoints</h3>
 * All CRUD goes through {@code /v2/items/{collection}}:
 * <ul>
 *   <li>GET    /v2/items/{c}?from-ofs=true&amp;data=&lt;url-encoded-json&gt;&amp;offset=N&amp;limit=N</li>
 *   <li>POST   /v2/items/{c}?from-ofs=true   — create</li>
 *   <li>PUT    /v2/items/{c}?from-ofs=true   — update (guid required in body)</li>
 *   <li>DELETE /v2/items/{c}/{guid}?from-ofs=true</li>
 *   <li>POST   /v2/items/{c}/aggregation     — pipeline queries</li>
 * </ul>
 *
 * <p>Note: the runtime API does not expose a "list all tables" endpoint.
 * {@link #listTables} and {@link #getTableSchema} therefore throw
 * {@link UnsupportedOperationException}.
 */
@Component
public class UCodeProvider implements DatabaseProvider {

    private static final String DEFAULT_BASE_URL = "https://api.client.u-code.io";

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public UCodeProvider() {
        this.http = RestClient.builder().build();
    }

    private String baseUrl() {
        return System.getProperty("ucode.base-url", DEFAULT_BASE_URL);
    }

    @Override
    public boolean supports(ConnectionParams params) {
        return params.isUCode();
    }

    private HttpHeaders authHeaders(ConnectionParams p) {
        HttpHeaders h = new HttpHeaders();
        // Exact headers the Go SDK uses.
        h.set("authorization", "API-KEY");
        h.set("X-API-KEY", p.appId());
        if (p.environmentId() != null) h.set("environment-id", p.environmentId());
        if (p.projectId() != null) h.set("project-id", p.projectId());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(HttpMethod method, String uri, HttpHeaders headers, Object body) {
        ResponseEntity<Map> resp = http.method(method)
            .uri(uri)
            .headers(h -> h.addAll(headers))
            .body(body == null ? "" : body)
            .retrieve()
            .toEntity(Map.class);
        Map<?, ?> raw = resp.getBody();
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }

    @Override
    public List<TableInfo> listTables(ConnectionParams params) {
        throw new UnsupportedOperationException(
            "u-code runtime API does not expose a list-tables endpoint. "
                + "Provide the collection (table) names explicitly, then call getTableSchema/select per table. "
                + "(Discovery is only available from the u-code admin console, which requires a user JWT.)");
    }

    /**
     * u-code has no schema endpoint at the runtime API, so we infer the schema
     * by fetching one row and reading its keys + value types. Good enough to
     * generate Postman request bodies.
     */
    @Override
    public TableInfo getTableSchema(ConnectionParams params, String table) throws JsonProcessingException {
        List<Map<String, Object>> sample = select(params, table, null, null, 1, 0);
        List<TableInfo.ColumnInfo> cols = new ArrayList<>();
        if (!sample.isEmpty()) {
            for (Map.Entry<String, Object> e : sample.get(0).entrySet()) {
                cols.add(new TableInfo.ColumnInfo(
                    e.getKey(),
                    jsonType(e.getValue()),
                    e.getValue() == null,
                    "guid".equals(e.getKey()),
                    null
                ));
            }
        }
        return new TableInfo(table, null, cols);
    }

    private static String jsonType(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof Number n) return (n instanceof Integer || n instanceof Long) ? "integer" : "number";
        if (v instanceof List<?>) return "array";
        if (v instanceof Map<?, ?>) return "object";
        return "string";
    }

    @Override
    public List<Map<String, Object>> select(
            ConnectionParams params, String table, List<String> columns,
            Map<String, Object> where, Integer limit, Integer offset) throws JsonProcessingException {

        // Build the 'data' query-string parameter the SDK sends.
        Map<String, Object> data = new LinkedHashMap<>();
        if (where != null && !where.isEmpty()) data.put("data", where); // SDK wraps filters under "data"
        if (columns != null && !columns.isEmpty()) data.put("columns", columns);

        int lim = limit == null ? 10 : limit;
        int off = offset == null ? 0 : offset;

        String dataJson = mapper.writeValueAsString(data);
        String encoded = URLEncoder.encode(dataJson, StandardCharsets.UTF_8);

        String uri = baseUrl() + "/v2/items/" + table
            + "?from-ofs=true&data=" + encoded + "&offset=" + off + "&limit=" + lim;

        Map<String, Object> resp = exchange(HttpMethod.GET, uri, authHeaders(params), null);
        return extractRows(resp);
    }

    @Override
    public Map<String, Object> insert(ConnectionParams params, String table, Map<String, Object> values) {
        String uri = baseUrl() + "/v2/items/" + table + "?from-ofs=true";
        Map<String, Object> body = Map.of("data", values);
        Map<String, Object> resp = exchange(HttpMethod.POST, uri, authHeaders(params), body);
        return extractSingleRow(resp);
    }

    @Override
    public Map<String, Object> update(
            ConnectionParams params, String table,
            Map<String, Object> values, Map<String, Object> where) {
        String uri = baseUrl() + "/v2/items/" + table + "?from-ofs=true";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(values);
        if (where != null) payload.putAll(where); // must include "guid"
        Map<String, Object> body = Map.of("data", payload);
        Map<String, Object> resp = exchange(HttpMethod.PUT, uri, authHeaders(params), body);
        return extractSingleRow(resp);
    }

    @Override
    public Map<String, Object> delete(
            ConnectionParams params, String table, Map<String, Object> where) {
        if (where == null || where.get("guid") == null) {
            throw new IllegalArgumentException(
                "u-code delete requires a 'guid' in the where clause");
        }
        String uri = baseUrl() + "/v2/items/" + table + "/" + where.get("guid") + "?from-ofs=true";
        Map<String, Object> resp = exchange(HttpMethod.DELETE, uri, authHeaders(params), null);
        Object status = resp.get("status");
        return Map.of("status", status == null ? "done" : status.toString());
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractRows(Map<String, Object> resp) {
        // u-code response shape: { data: { data: { response: [...] } }, status: ... }
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> d1) {
            Object inner = d1.get("data");
            if (inner instanceof Map<?, ?> d2) {
                Object response = d2.get("response");
                if (response instanceof List<?> l) return (List<Map<String, Object>>) (List<?>) l;
            } else if (inner instanceof List<?> l) {
                return (List<Map<String, Object>>) (List<?>) l;
            }
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractSingleRow(Map<String, Object> resp) {
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> d1) {
            Object inner = d1.get("data");
            if (inner instanceof Map<?, ?> d2) return (Map<String, Object>) d2;
        }
        return resp;
    }
}
