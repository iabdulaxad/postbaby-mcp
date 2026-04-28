package echo.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;


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
