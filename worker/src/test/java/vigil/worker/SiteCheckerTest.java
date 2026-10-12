package vigil.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Plain unit test: a local JDK HttpServer plays the monitored site, no Spring context and no Docker needed.
class SiteCheckerTest {
  private static final long CONNECT_TIMEOUT_MS = 1000;
  private static final long REQUEST_TIMEOUT_MS = 500;

  private static HttpServer server;
  private static String baseUrl;
  private final SiteChecker siteChecker = new SiteChecker(CONNECT_TIMEOUT_MS, REQUEST_TIMEOUT_MS);

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/ok", exchange -> respond(exchange, 200));
    server.createContext("/error", exchange -> respond(exchange, 500));
    server.createContext("/missing", exchange -> respond(exchange, 404));
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().add("Location", "/ok");
      respond(exchange, 302);
    });
    server.createContext("/slow", exchange -> {
      try {
        Thread.sleep(REQUEST_TIMEOUT_MS * 4);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      respond(exchange, 200);
    });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  private static void respond(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  @Test
  void siteThatWorksIsUp() {
    CheckResult result = siteChecker.checkSite(baseUrl + "/ok");

    assertThat(result.up()).isTrue();
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.error()).isNull();
    assertThat(result.responseTimeMs()).isGreaterThanOrEqualTo(0);
    assertThat(result.checkedAt()).isNotNull();
  }

  @Test
  void serverErrorIsDown() {
    CheckResult result = siteChecker.checkSite(baseUrl + "/error");

    assertThat(result.up()).isFalse();
    assertThat(result.statusCode()).isEqualTo(500);
  }

  @Test
  void redirectsAreFollowed() {
    CheckResult result = siteChecker.checkSite(baseUrl + "/redirect");

    assertThat(result.up()).isTrue();
    assertThat(result.statusCode()).isEqualTo(200);
  }

  @Test
  void notFoundIsDown() {
    CheckResult result = siteChecker.checkSite(baseUrl + "/missing");

    assertThat(result.up()).isFalse();
    assertThat(result.statusCode()).isEqualTo(404);
  }

  @Test
  void unknownDomainIsDown() {
    // .invalid is reserved (RFC 2606) and never resolves
    CheckResult result = siteChecker.checkSite("http://does-not-exist.invalid/");

    assertThat(result.up()).isFalse();
    assertThat(result.statusCode()).isNull();
    assertThat(result.error()).isNotBlank();
  }

  @Test
  void errorTextIsAtMost500Characters() throws Exception {
    // A malformed status line makes HttpClient throw a ProtocolException that quotes the whole line (700+ chars)
    try (ServerSocket brokenServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Thread.ofVirtual().start(() -> {
        try (Socket socket = brokenServer.accept()) {
          socket.getInputStream().read(new byte[4096]);
          OutputStream out = socket.getOutputStream();
          out.write(("HTTP/1.1 " + "x".repeat(700) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          out.flush();
        }
        catch (IOException e) {
          // the test fails on its own assertions if the server never answers
        }
      });

      CheckResult result = siteChecker.checkSite("http://127.0.0.1:" + brokenServer.getLocalPort() + "/");

      assertThat(result.up()).isFalse();
      assertThat(result.error()).hasSize(500);
    }
  }

  @Test
  void slowSiteTimesOut() {
    CheckResult result = siteChecker.checkSite(baseUrl + "/slow");

    assertThat(result.up()).isFalse();
    assertThat(result.statusCode()).isNull();
    assertThat(result.error()).isEqualTo("time out");
    assertThat(result.responseTimeMs()).isEqualTo((int) REQUEST_TIMEOUT_MS);
  }
}
