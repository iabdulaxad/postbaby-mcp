package echo.mcp.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionParamsTest {

    @Test
    void isUCode_trueWhenAppIdProvided() {
        ConnectionParams p = new ConnectionParams("app-123", null, null, null, null, null, null, null);
        assertTrue(p.isUCode());
        assertFalse(p.isPostgres());
    }

    @Test
    void isUCode_falseWhenAppIdBlank() {
        ConnectionParams p = new ConnectionParams("   ", null, null, null, null, null, null, null);
        assertFalse(p.isUCode());
    }

    @Test
    void isPostgres_requiresAllCoreFields() {
        ConnectionParams missingDb = new ConnectionParams(
            null, null, null,
            "localhost", 5432, null, "user", "pw");
        assertFalse(missingDb.isPostgres());

        ConnectionParams complete = new ConnectionParams(
            null, null, null,
            "localhost", 5432, "mydb", "user", "pw");
        assertTrue(complete.isPostgres());
        assertFalse(complete.isUCode());
    }

    @Test
    void validate_throwsWhenNeitherCredentialSetIsComplete() {
        ConnectionParams empty = new ConnectionParams(null, null, null, null, null, null, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, empty::validate);
        assertTrue(ex.getMessage().contains("appId") || ex.getMessage().contains("Postgres"));
    }

    @Test
    void validate_passesForUCode() {
        ConnectionParams p = new ConnectionParams("app", "env", "proj", null, null, null, null, null);
        assertDoesNotThrow(p::validate);
    }

    @Test
    void validate_passesForPostgres() {
        ConnectionParams p = new ConnectionParams(null, null, null, "h", 5432, "db", "u", "pw");
        assertDoesNotThrow(p::validate);
    }
}
