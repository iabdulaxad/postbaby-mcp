package echo.mcp.it;

import echo.mcp.model.ConnectionParams;
import echo.mcp.tools.DatabaseTools;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseToolsSteps {

  @Autowired
  private DatabaseTools tools;
  @Autowired
  private MockWebServer ucodeMockServer;
  @Autowired
  private TestState state;

  @Before
  public void resetSchema() throws Exception {
    try (Connection c = DriverManager.getConnection(IntegrationTestConfig.H2_URL, "sa", "");
         Statement st = c.createStatement()) {
      st.execute("DROP ALL OBJECTS");
    }
    while (ucodeMockServer.getRequestCount() < 0) {
    }
  }


  @Given("a Postgres connection is configured")
  public void postgresConnection() {
    state.connection = new ConnectionParams(
      null, null, null, "h2", 5432, "cuketest", "sa", "");
  }

  @Given("a u-code connection is configured with appId {string}")
  public void ucodeConnection(String appId) {
    state.connection = new ConnectionParams(appId, "env-1", "proj-1",
      null, null, null, null, null);
  }

  @Given("an empty connection is configured")
  public void emptyConnection() {
    state.connection = new ConnectionParams(null, null, null, null, null, null, null, null);
  }


  @Given("a Postgres table {string} exists with columns:")
  public void createPostgresTable(String table, DataTable columns) throws Exception {
    StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(table).append(" (");
    List<Map<String, String>> rows = columns.asMaps();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, String> r = rows.get(i);
      if (i > 0) ddl.append(", ");
      ddl.append(r.get("name")).append(" ").append(r.get("type"));
      if ("true".equalsIgnoreCase(r.getOrDefault("primary_key", "false"))) {
        ddl.append(" PRIMARY KEY");
      }
    }
    ddl.append(")");

    try (Connection c = DriverManager.getConnection(IntegrationTestConfig.H2_URL, "sa", "");
         Statement st = c.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + table);
      st.execute(ddl.toString());
    }
  }

  @Given("the u-code mock will respond with body:")
  public void enqueueUcode(String body) {
    ucodeMockServer.enqueue(new MockResponse()
      .setHeader("Content-Type", "application/json")
      .setBody(body));
  }


  @When("I list tables")
  public void listTables() {
    try {
      state.tablesResult = tools.listTables(state.connection);
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I describe table {string}")
  public void describeTable(String table) {
    try {
      state.schemaResult = tools.getTableSchema(state.connection, table);
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I insert into {string}:")
  public void insert(String table, DataTable data) {
    Map<String, Object> values = new LinkedHashMap<>(data.asMap());
    try {
      state.mutationResult = tools.insert(state.connection, table, values);
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I select from {string} where:")
  public void select(String table, DataTable where) {
    Map<String, Object> filter = new LinkedHashMap<>(where.asMap());
    try {
      state.selectResult = tools.select(state.connection, table, null, filter, null, null);
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I select all from {string}")
  public void selectAll(String table) {
    try {
      state.selectResult = tools.select(state.connection, table, null, null, null, null);
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I update {string} set name to {string} where name is {string}")
  public void update(String table, String newName, String oldName) {
    try {
      state.mutationResult = tools.update(state.connection, table,
        Map.of("name", newName), Map.of("name", oldName));
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I delete from {string} where name is {string}")
  public void delete(String table, String name) {
    try {
      state.mutationResult = tools.delete(state.connection, table,
        Map.of("name", name));
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I delete from {string} with no filter")
  public void deleteNoFilter(String table) {
    try {
      state.mutationResult = tools.delete(state.connection, table, new HashMap<>());
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  @When("I delete u-code item {string} from {string}")
  public void deleteUcode(String guid, String table) {
    try {
      state.mutationResult = tools.delete(state.connection, table, Map.of("guid", guid));
    } catch (Throwable t) {
      state.lastError = t;
    }
  }

  // --- Then: assertions ---

  @Then("the table list contains {string}")
  public void tableListContains(String name) {
    assertNotNull(state.tablesResult, "no listTables result");
    assertTrue(state.tablesResult.stream()
        .anyMatch(t -> name.equalsIgnoreCase(t.name())),
      "expected to find table " + name + " in " + state.tablesResult);
  }

  @Then("the schema reports column {string} as primary key")
  public void primaryKey(String name) {
    assertNotNull(state.schemaResult);
    assertTrue(state.schemaResult.columns().stream()
      .anyMatch(c -> name.equalsIgnoreCase(c.name()) && c.primaryKey()));
  }

  @Then("the result row count is {int}")
  public void rowCount(int expected) {
    assertNotNull(state.selectResult);
    assertEquals(expected, state.selectResult.size());
  }

  @Then("the affected row count is {int}")
  public void affected(int expected) {
    assertNotNull(state.mutationResult);
    assertEquals(expected, ((Number) state.mutationResult.get("affected")).intValue());
  }

  @Then("the inserted row has {string} = {string}")
  public void insertedHas(String key, String value) {
    assertNotNull(state.mutationResult);
    assertEquals(value, String.valueOf(state.mutationResult.get(key)));
  }

  @Then("the operation should fail with {string}")
  public void shouldFail(String fragment) {
    assertNotNull(state.lastError, "expected an error but none was raised");
    assertTrue(state.lastError.getMessage() != null
        && state.lastError.getMessage().toLowerCase().contains(fragment.toLowerCase()),
      "expected error containing '" + fragment + "', got: " + state.lastError);
  }

  @And("the u-code mock received a {string} request to path containing {string}")
  public void ucodeRequest(String method, String pathFragment) throws Exception {
    var req = ucodeMockServer.takeRequest();
    assertEquals(method, req.getMethod());
    assertTrue(req.getPath().contains(pathFragment),
      "expected path to contain " + pathFragment + " but was " + req.getPath());
  }
}
