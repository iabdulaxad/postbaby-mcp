package echo.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Credentials supplied per MCP tool call.
 *
 * <p>Provide either u-code credentials (at minimum {@code appId}) or a full
 * Postgres set ({@code pgHost}, {@code pgDatabase}, {@code pgUser}, {@code pgPassword}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionParams(
    // u-code
    String appId,
    String environmentId,
    String projectId,

    // postgres
    String pgHost,
    Integer pgPort,
    String pgDatabase,
    String pgUser,
    String pgPassword
) {
    public boolean isUCode() {
        // The u-code runtime SDK only requires AppId; environmentId is optional.
        return appId != null && !appId.isBlank();
    }

    public boolean isPostgres() {
        return pgHost != null && !pgHost.isBlank()
            && pgUser != null && pgPassword != null && pgDatabase != null;
    }

    public void validate() {
        if (!isUCode() && !isPostgres()) {
            throw new IllegalArgumentException(
                "Connection params invalid: supply either 'appId' (u-code) "
                    + "or (pgHost + pgDatabase + pgUser + pgPassword) for Postgres.");
        }
    }
}
