package echo.mcp.it;

import echo.mcp.model.ConnectionParams;
import echo.mcp.provider.PostgresProvider;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@TestConfiguration
public class IntegrationTestConfig {

    public static final String H2_URL =
        "jdbc:h2:mem:cuketest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Bean(name = "postgresProvider")
    @Primary
    public PostgresProvider testPostgresProvider() {
        return new PostgresProvider() {
            @Override
            protected Connection open(ConnectionParams p) throws SQLException {
                return DriverManager.getConnection(H2_URL, "sa", "");
            }

            @Override
            protected String wrapInsertReturning(String insertSql) {
                return "SELECT * FROM FINAL TABLE (" + insertSql + ")";
            }
        };
    }

    @Bean(destroyMethod = "shutdown")
    public MockWebServer ucodeMockServer() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        String url = server.url("").toString();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        System.setProperty("ucode.base-url", url);
        return server;
    }
}
