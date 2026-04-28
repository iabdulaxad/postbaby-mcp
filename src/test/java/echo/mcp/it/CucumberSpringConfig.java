package echo.mcp.it;

import echo.Application;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@CucumberContextConfiguration
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.main.allow-bean-definition-overriding=true")
@Import(IntegrationTestConfig.class)
public class CucumberSpringConfig {
}
