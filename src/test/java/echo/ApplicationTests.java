package echo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ApplicationTests {

  @Autowired
  private TestRestTemplate rest;

  @Test
  public void testEchoPost() throws Exception {
    String input = "hello";

    ResponseEntity<String> response = this.rest.exchange(
        RequestEntity.post(new URI("/echo"))
            .contentType(MediaType.TEXT_PLAIN)
            .body(input),
        String.class);

    assertThat(response.getStatusCode().value(), equalTo(200));
    assertThat(response.getBody(), notNullValue());
    assertThat(response.getBody(), equalTo(input));
  }

  @Test
  public void testEchoGetWithQueryParam() throws Exception {
    ResponseEntity<String> response = this.rest.exchange(
        RequestEntity.get(new URI("/echo?message=world")).build(),
        String.class);

    assertThat(response.getStatusCode().value(), equalTo(200));
    assertThat(response.getBody(), containsString("world"));
  }

  @Test
  public void testEchoGetWithPathVariable() throws Exception {
    ResponseEntity<String> response = this.rest.exchange(
        RequestEntity.get(new URI("/echo/knative")).build(),
        String.class);

    assertThat(response.getStatusCode().value(), equalTo(200));
    assertThat(response.getBody(), containsString("knative"));
  }
}
