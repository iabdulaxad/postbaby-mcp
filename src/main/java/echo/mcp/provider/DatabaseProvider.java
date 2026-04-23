package echo.mcp.provider;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;

import java.util.List;
import java.util.Map;

public interface DatabaseProvider {

    boolean supports(ConnectionParams params);

    List<TableInfo> listTables(ConnectionParams params) throws Exception;

    TableInfo getTableSchema(ConnectionParams params, String table) throws Exception;

    List<Map<String, Object>> select(
        ConnectionParams params,
        String table,
        List<String> columns,
        Map<String, Object> where,
        Integer limit,
        Integer offset
    ) throws Exception;

    Map<String, Object> insert(
        ConnectionParams params,
        String table,
        Map<String, Object> values
    ) throws Exception;

    Map<String, Object> update(
        ConnectionParams params,
        String table,
        Map<String, Object> values,
        Map<String, Object> where
    ) throws Exception;

    Map<String, Object> delete(
        ConnectionParams params,
        String table,
        Map<String, Object> where
    ) throws Exception;
}
