package echo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/echo")
public class EchoController {

  private static final Logger LOGGER = Logger.getLogger(EchoController.class.getName());

  @GetMapping
  public ResponseEntity<Map<String, String>> echoGet(
      @RequestParam(value = "message", defaultValue = "hello") String message) {
    LOGGER.log(Level.INFO, "GET /echo message={0}", message);
    return ResponseEntity.ok(Map.of("message", message));
  }

  @GetMapping("/{message}")
  public ResponseEntity<Map<String, String>> echoPath(@PathVariable String message) {
    LOGGER.log(Level.INFO, "GET /echo/{0}", message);
    return ResponseEntity.ok(Map.of("message", message));
  }

  @PostMapping
  public ResponseEntity<String> echoPost(@RequestBody String body) {
    LOGGER.log(Level.INFO, "POST /echo body={0}", body);
    return ResponseEntity.ok(body);
  }
}
