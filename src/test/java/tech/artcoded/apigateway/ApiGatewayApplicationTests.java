package tech.artcoded.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Starts the gateway with the bundled gateway.yml, pointing every route to a local fake backend
 * that echoes the path + query it received. Guards the Spring Cloud Gateway 5 property prefix and the rewrites.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

  private static final HttpServer BACKEND = startBackend();

  private static HttpServer startBackend() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        var uri = exchange.getRequestURI();
        var body = (uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""))
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        try (var os = exchange.getResponseBody()) {
          os.write(body);
        }
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterAll
  static void stopBackend() {
    BACKEND.stop(0);
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    String backend = "http://127.0.0.1:" + BACKEND.getAddress().getPort();
    registry.add("BACKEND_URL", () -> backend);
    // only the chart url is set: the search route must fall back to it (backward compatible)
    registry.add("YAHOO_FINANCE_CHART_URL", () -> backend);
  }

  @Value("${local.server.port}")
  private int port;

  @Autowired
  private RouteLocator routeLocator;

  private WebTestClient client;

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void routesFromGatewayYmlAreLoaded() {
    List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();
    assertThat(ids).contains("yahoo_finance_chart", "yahoo_finance_search", "api");
  }

  @Test
  void apiIsForwardedAsIs() {
    client.get().uri("/api/invoices/42?page=1").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("/api/invoices/42?page=1");
  }

  @Test
  void yahooChartIsRewritten() {
    client.get().uri("/proxy/yahoo-finance/chart/AAPL?range=1d&interval=1h").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("/v8/finance/chart/AAPL?range=1d&interval=1h");
  }

  @Test
  void yahooSearchIsRewritten() {
    client.get().uri("/proxy/yahoo-finance/search?q=apple").exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo("/v1/finance/search?q=apple");
  }

  @Test
  void unknownPathIsNotFound() {
    client.get().uri("/nope").exchange().expectStatus().isNotFound();
  }
}
