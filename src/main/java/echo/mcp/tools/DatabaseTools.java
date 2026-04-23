package echo.mcp.tools;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import echo.mcp.provider.DatabaseProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP tools. Each call takes a {@link ConnectionParams} so one server can
 * serve many databases — the client chooses per-call whether to use u-code
 * credentials (AppId [+ EnvironmentId]) or direct Postgres credentials.
 */
@Service
public class DatabaseTools {

    private final List<DatabaseProvider> providers;

    public DatabaseTools(List<DatabaseProvider> providers) {
        this.providers = providers;
    }

    private DatabaseProvider resolve(ConnectionParams params) {
        params.validate();
        return providers.stream()
            .filter(p -> p.supports(params))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No provider supports the given ConnectionParams"));
    }

    @Tool(description = """
        List tables in the target database.
        For Postgres this uses JDBC DatabaseMetaData.
        NOTE: the u-code runtime API does not expose a list-tables endpoint;
        callers using u-code credentials must know their collection names.
        """)
    public List<TableInfo> listTables(
            @ToolParam(description = "Credentials: either appId (u-code) or full pg* fields") ConnectionParams connection
    ) throws Exception {
        return resolve(connection).listTables(connection);
    }

    @Tool(description = """
        Describe a table (columns, types, primary keys, nullability).
        Postgres-only; not supported for u-code (use select(limit=1) to sample instead).
        """)
    public TableInfo getTableSchema(
            ConnectionParams connection,
            @ToolParam(description = "Table / u-code collection name") String table
    ) throws Exception {
        return resolve(connection).getTableSchema(connection, table);
    }

    @Tool(description = """
        Read rows from a table. Optional column projection, equality filters, limit, offset.
        For u-code, 'where' is passed as the filter map the client API expects.
        """)
    public List<Map<String, Object>> select(
            ConnectionParams connection,
            String table,
            @ToolParam(required = false, description = "Columns to return; null/empty means all") List<String> columns,
            @ToolParam(required = false, description = "Equality filters keyed by column name") Map<String, Object> where,
            @ToolParam(required = false) Integer limit,
            @ToolParam(required = false) Integer offset
    ) throws Exception {
        return resolve(connection).select(connection, table, columns, where, limit, offset);
    }

    @Tool(description = "Insert a single row. Returns the created row.")
    public Map<String, Object> insert(
            ConnectionParams connection,
            String table,
            @ToolParam(description = "Column -> value map") Map<String, Object> values
    ) throws Exception {
        return resolve(connection).insert(connection, table, values);
    }

    @Tool(description = """
        Update rows matching 'where' with the given values.
        For u-code, include the row's 'guid' in 'where'.
        """)
    public Map<String, Object> update(
            ConnectionParams connection,
            String table,
            Map<String, Object> values,
            Map<String, Object> where
    ) throws Exception {
        return resolve(connection).update(connection, table, values, where);
    }

    @Tool(description = """
        Delete rows matching 'where'. A non-empty 'where' is required.
        For u-code, 'where' must contain the row's 'guid'.
        """)
    public Map<String, Object> delete(
            ConnectionParams connection,
            String table,
            Map<String, Object> where
    ) throws Exception {
        return resolve(connection).delete(connection, table, where);
    }
}
