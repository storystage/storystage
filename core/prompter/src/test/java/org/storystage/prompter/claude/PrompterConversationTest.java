/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The request loop against a local fake of the streaming Messages API. No real key or network. */
public class PrompterConversationTest {
  private static final String FAKE_KEY = "sk-ant-fake" + "0123456789abcdefghij";
  private HttpServer server;
  private final Deque<String> responses = new ArrayDeque<>();
  private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
  private final List<String> betaHeaders = Collections.synchronizedList(new ArrayList<>());

  private static String sse(String... events) {
    StringBuilder sb = new StringBuilder();
    for (String e : events) {
      String type = e.substring(e.indexOf("\"type\":\"") + 8);
      type = type.substring(0, type.indexOf('"'));
      sb.append("event: ").append(type).append('\n').append("data: ").append(e).append("\n\n");
    }
    return sb.toString();
  }

  private static String messageStart(String id) {
    return "{\"type\":\"message_start\",\"message\":{\"id\":\"" + id + "\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
        + "\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":1}}}";
  }

  private static String messageEnd(String stopReason) {
    return "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":20}}";
  }

  @Before
  public void startServer() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext("/", exchange -> {
      try (InputStream in = exchange.getRequestBody()) {
        this.requestBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
      String beta = exchange.getRequestHeaders().getFirst("anthropic-beta");
      this.betaHeaders.add(beta != null ? beta : "");
      String body;
      synchronized (this.responses) {
        body = this.responses.isEmpty() ? sse(messageStart("msg_x"), messageEnd("end_turn"), "{\"type\":\"message_stop\"}") : this.responses.removeFirst();
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    this.server.start();
    System.setProperty(ClaudeClients.BASE_URL_PROPERTY, "http://127.0.0.1:" + this.server.getAddress().getPort());
  }

  @After
  public void stopServer() {
    this.server.stop(0);
    System.clearProperty(ClaudeClients.BASE_URL_PROPERTY);
  }

  private static Tool explainTool() {
    return Tool.builder()
        .name("explain")
        .description("Explain something to the learner.")
        .inputSchema(Tool.InputSchema.builder()
            .properties(Tool.InputSchema.Properties.builder()
                .putAdditionalProperty("text", JsonValue.from(Map.of("type", "string")))
                .build())
            .required(List.of("text"))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build())
        .strict(true)
        .build();
  }

  @Test
  public void toolCallThenAnswer() {
    this.responses.add(sse(
        messageStart("msg_1"),
        "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"explain\",\"input\":{}}}",
        "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"text\\\": \\\"A loop repeats\"}}",
        "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" blocks.\\\"}\"}}",
        "{\"type\":\"content_block_stop\",\"index\":0}",
        messageEnd("tool_use"),
        "{\"type\":\"message_stop\"}"));
    this.responses.add(sse(
        messageStart("msg_2"),
        "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
        "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"All done.\"}}",
        "{\"type\":\"content_block_stop\",\"index\":0}",
        messageEnd("end_turn"),
        "{\"type\":\"message_stop\"}"));

    List<String> seenInputs = new ArrayList<>();
    ToolExecutor executor = (name, input) -> {
      seenInputs.add(name + ":" + Json.tree(input).path("text").asText());
      return ToolExecutor.Result.ok("{\"ok\":true}", null);
    };
    StringBuilder text = new StringBuilder();
    List<String> events = new ArrayList<>();
    AnthropicClient client = ClaudeClients.forRequests(FAKE_KEY);
    PrompterConversation conversation = new PrompterConversation(client, "You are the Prompter.", List.of(explainTool()), executor);
    PrompterConversation.Outcome outcome = conversation.send("What does a loop do?", "class Scene {}", null, PrompterConversation.Mode.EXPLAIN_ONLY, new PrompterConversation.Listener() {
      @Override
      public void textDelta(String t) {
        text.append(t);
      }

      @Override
      public void toolStarted(String toolName) {
        events.add("start " + toolName);
      }

      @Override
      public void toolFinished(String toolName, ToolExecutor.Result result) {
        events.add("done " + toolName + " " + result.isOk());
      }

      @Override
      public void finished(PrompterConversation.Outcome o, String message) {
        events.add("finished " + o);
      }
    });
    client.close();

    assertEquals(PrompterConversation.Outcome.DONE, outcome);
    assertEquals(List.of("explain:A loop repeats blocks."), seenInputs);
    assertEquals("All done.", text.toString());
    assertEquals(List.of("start explain", "done explain true", "finished DONE"), events);
    assertEquals(2, this.requestBodies.size());
    String second = this.requestBodies.get(1);
    assertTrue("tool result sent back", second.contains("tool_result") && second.contains("toolu_1"));
    assertTrue("fallbacks requested", this.requestBodies.get(0).contains("\"fallbacks\":\"default\""));
    assertTrue("fallback beta header", this.betaHeaders.get(0).contains(PrompterConversation.FALLBACK_BETA));
    assertTrue("effort set", this.requestBodies.get(0).contains("\"effort\":\"low\""));
    assertTrue("key never in the body", !this.requestBodies.get(0).contains(FAKE_KEY));
    // user request, assistant tool use, tool result, assistant answer
    assertEquals(4, conversation.getHistory().size());
  }

  @Test
  public void refusalIsReported() {
    this.responses.add(sse(messageStart("msg_r"), messageEnd("refusal"), "{\"type\":\"message_stop\"}"));
    AnthropicClient client = ClaudeClients.forRequests(FAKE_KEY);
    PrompterConversation conversation = new PrompterConversation(client, "You are the Prompter.", List.of(), (n, i) -> ToolExecutor.Result.ok("{}", null));
    List<String> messages = new ArrayList<>();
    PrompterConversation.Outcome outcome = conversation.send("something unsuitable", "", null, PrompterConversation.Mode.BUILD_IT, new PrompterConversation.Listener() {
      @Override
      public void textDelta(String t) {
      }

      @Override
      public void toolStarted(String toolName) {
      }

      @Override
      public void toolFinished(String toolName, ToolExecutor.Result result) {
      }

      @Override
      public void finished(PrompterConversation.Outcome o, String message) {
        messages.add(message);
      }
    });
    client.close();
    assertEquals(PrompterConversation.Outcome.REFUSED, outcome);
    assertTrue(messages.get(0).contains("can't help"));
  }
}
