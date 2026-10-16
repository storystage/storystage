/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/** JSON helpers for tool inputs and results. */
public final class Json {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  private Json() {
    throw new AssertionError();
  }

  /** Writes any value (maps, lists, strings, numbers) as JSON text. */
  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write JSON", e);
    }
  }

  /** A tool input from the SDK as a Jackson tree; null input becomes a JSON null. */
  public static JsonNode tree(JsonValue input) {
    if (input == null) {
      return NullNode.getInstance();
    }
    JsonNode node = input.convert(JsonNode.class);
    return node != null ? node : NullNode.getInstance();
  }
}
