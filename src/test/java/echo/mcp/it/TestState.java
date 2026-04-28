package echo.mcp.it;

import echo.mcp.model.ConnectionParams;
import echo.mcp.model.TableInfo;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ScenarioScope
public class TestState {
    public ConnectionParams connection;
    public List<TableInfo> tablesResult;
    public TableInfo schemaResult;
    public List<Map<String, Object>> selectResult;
    public Map<String, Object> mutationResult;
    public Throwable lastError;
    public final Map<String, Object> insertedKeys = new HashMap<>();
}
