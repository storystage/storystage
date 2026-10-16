/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import java.time.Duration;

/**
 * Builds Claude API clients from the user's own key. The key is passed explicitly; the
 * ANTHROPIC_API_KEY environment variable is never used. See docs/spec/01-prompter-protocol.md §1.
 */
public final class ClaudeClients {
  /** The model the Prompter uses. */
  public static final String MODEL = "claude-opus-5-5";

  /** Overrides the API address; for tests with a fake server only. */
  static final String BASE_URL_PROPERTY = "storystage.prompter.testBaseUrl";

  private ClaudeClients() {
    throw new AssertionError();
  }

  /** A client for Prompter requests: streaming responses can take minutes. */
  public static AnthropicClient forRequests(String apiKey) {
    return builder(apiKey).timeout(Duration.ofMinutes(10)).maxRetries(2).build();
  }

  /** A client for the quick key check in the set-up wizard: no retries, short timeout. */
  public static AnthropicClient forKeyCheck(String apiKey) {
    return builder(apiKey).timeout(Duration.ofSeconds(20)).maxRetries(0).build();
  }

  private static AnthropicOkHttpClient.Builder builder(String apiKey) {
    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
    String testBaseUrl = System.getProperty(BASE_URL_PROPERTY);
    if (testBaseUrl != null && !testBaseUrl.isEmpty()) {
      builder.baseUrl(testBaseUrl);
    }
    return builder;
  }
}
