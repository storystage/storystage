/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.core.JsonValue;

/**
 * Runs one tool call from Claude. The executor is responsible for applying world edits on the
 * Swing thread. See docs/spec/01-prompter-protocol.md §3.
 */
public interface ToolExecutor {
  /** Runs the named tool with Claude's input and returns the result to send back. */
  Result execute(String toolName, JsonValue input);

  /** The outcome of one tool call: a JSON text for Claude, and whether it failed. */
  final class Result {
    private final boolean ok;
    private final String json;
    private final String narration;

    private Result(boolean ok, String json, String narration) {
      this.ok = ok;
      this.json = json;
      this.narration = narration;
    }

    /** A successful step; {@code narration} is spoken and captioned, and may be null. */
    public static Result ok(String json, String narration) {
      return new Result(true, json, narration);
    }

    /** A failed step, with an error code from spec 01 §3 and a message Claude can act on. */
    public static Result error(String code, String message) {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("ok", false);
      map.put("error", code);
      map.put("message", message);
      return new Result(false, Json.write(map), null);
    }

    public boolean isOk() {
      return this.ok;
    }

    public String getJson() {
      return this.json;
    }

    public String getNarration() {
      return this.narration;
    }
  }
}
