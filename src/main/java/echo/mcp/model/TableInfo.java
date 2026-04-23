package echo.mcp.model;

import java.util.List;

public record TableInfo(String name, String schema, List<ColumnInfo> columns) {
    public record ColumnInfo(
        String name,
        String type,
        boolean nullable,
        boolean primaryKey,
        String defaultValue
    ) {}
}
