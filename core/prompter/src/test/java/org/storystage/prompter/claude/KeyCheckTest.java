/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/** Runs the key check against a local fake of the Anthropic API; no real key or network. */
public class KeyCheckTest {
  private static final String FAKE_KEY = "sk-ant-fake" + "0123456789abcdefghij";
  private HttpServer server;

  private void startServer(int status, String body) throws IOException {
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext("/", exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    this.server.start();
    System.setProperty(ClaudeClients.BASE_URL_PROPERTY, "http://127.0.0.1:" + this.server.getAddress().getPort());
  }

  @After
  public void stopServer() {
    if (this.server != null) {
      this.server.stop(0);
    }
    System.clearProperty(ClaudeClients.BASE_URL_PROPERTY);
  }

  @Test
  public void workingKey() throws IOException {
    this.startServer(200, "{\"id\":\"" + ClaudeClients.MODEL + "\",\"type\":\"model\",\"display_name\":\"Claude Opus 5.5\",\"created_at\":\"2026-01-01T00:00:00Z\"}");
    assertEquals(KeyCheck.Result.OK, KeyCheck.check(FAKE_KEY));
  }

  @Test
  public void rejectedKey() throws IOException {
    this.startServer(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
    assertEquals(KeyCheck.Result.REJECTED, KeyCheck.check(FAKE_KEY));
  }

  @Test
  public void keyWithoutModelAccess() throws IOException {
    this.startServer(404, "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"model not found\"}}");
    assertEquals(KeyCheck.Result.NO_MODEL_ACCESS, KeyCheck.check(FAKE_KEY));
  }

  @Test
  public void unreachable() throws IOException {
    this.startServer(200, "{}");
    int port = this.server.getAddress().getPort();
    this.server.stop(0);
    this.server = null;
    System.setProperty(ClaudeClients.BASE_URL_PROPERTY, "http://127.0.0.1:" + port);
    assertEquals(KeyCheck.Result.UNREACHABLE, KeyCheck.check(FAKE_KEY));
  }

  @Test
  public void notAKeyNeverContactsAnthropic() {
    assertEquals(KeyCheck.Result.NOT_A_KEY, KeyCheck.check("hello"));
  }
}
