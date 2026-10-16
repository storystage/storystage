/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Prompter's tools as strict JSON schemas. Every field is required (use "" or -1 where a
 * field doesn't apply) and no extra fields are allowed, so Claude's inputs always have the shape
 * the executor expects. See docs/spec/01-prompter-protocol.md §3.
 */
public final class ToolSchemas {
  public static final String EXPLAIN = "explain";
  public static final String READ_PROJECT = "read_project";
  public static final String ADD_MODEL = "add_model";
  public static final String SET_PROPERTY = "set_property";
  public static final String INSERT_STATEMENT = "insert_statement";
  public static final String INSERT_CONTROL = "insert_control";

  private ToolSchemas() {
    throw new AssertionError();
  }

  /** All tools, or only the read-only ones in explain-only mode. */
  public static List<Tool> tools(boolean explainOnly) {
    List<Tool> rv = new ArrayList<>();
    rv.add(tool(EXPLAIN, "Tell the learner something in plain words, without changing the world. Optionally flash blocks or objects.",
        props("text", str("What to say, under 120 words, in the learner's language."),
            "highlight", arr(str("A block id like a1f3 or an object name like cat."), "Blocks or objects to point out; may be empty."))));
    rv.add(tool(READ_PROJECT, "Get the latest text of the learner's world, with block ids, after several changes.", props()));
    if (explainOnly) {
      return rv;
    }
    rv.add(tool(ADD_MODEL, "Add a model from the gallery to the scene.",
        props("gallery_item", str("A gallery id from the gallery list, exactly as written."),
            "name", str("A short name for the object, like cat or redBalloon."),
            "position", obj(props("x", num("Metres across the camera's view from the scene centre."), "y", num("Metres up; 0 is the ground."), "z", num("Metres away from the camera; the camera starts near z = -8."))),
            "turn_degrees", num("Degrees to turn left from the model's default facing; 0 for none."),
            "narration", str("One short sentence describing the change, read aloud."))));
    rv.add(tool(SET_PROPERTY, "Change how an object starts in the scene: position (x, y, z in metres), turn_degrees (positive turns left), paint (a Color name) or opacity (0 to 1).",
        props("object", str("The object's name in the scene."),
            "property", enm("Which property to change.", "position", "turn_degrees", "paint", "opacity"),
            "value", str("For position \"x, y, z\"; for turn_degrees and opacity a number; for paint a Color name like RED."),
            "narration", str("One short sentence describing the change, read aloud."))));
    rv.add(tool(INSERT_STATEMENT, "Add one procedure block to a method, such as cat.move FORWARD 1.0 or cat.say \"Hello\".",
        props("method", str("Class.method to add to, like Scene.myFirstMethod. Ignored when parent_block is set."),
            "parent_block", str("Id of a control block to put this inside, or \"\" for the method's own body."),
            "position", integer("Where to insert: 0 for first, or -1 for the end."),
            "target", str("The object doing it: an object name like cat, or this for the scene."),
            "procedure", str("The procedure's name from the catalog, like move, turn, say or moveToward."),
            "arguments", arr(pair(), "Required inputs by parameter name from the catalog, like direction FORWARD and amount 1.0."),
            "details", arr(pair(), "Optional details by name, like duration 2.0; may be empty."),
            "narration", str("One short sentence describing the change, read aloud."))));
    rv.add(tool(INSERT_CONTROL, "Add a control block that later blocks can go inside. The result's ids.block is the parent_block to use.",
        props("method", str("Class.method to add to, like Scene.myFirstMethod. Ignored when parent_block is set."),
            "parent_block", str("Id of a control block to put this inside, or \"\"."),
            "position", integer("0 for first, or -1 for the end."),
            "kind", enm("The kind of block.", "do_in_order", "do_together", "count"),
            "count", integer("Times to repeat, for count; otherwise 0."),
            "narration", str("One short sentence describing the change, read aloud."))));
    return rv;
  }

  private static Tool tool(String name, String description, Map<String, Object> properties) {
    Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
    for (Map.Entry<String, Object> e : properties.entrySet()) {
      props.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
    }
    return Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(Tool.InputSchema.builder()
            .properties(props.build())
            .required(new ArrayList<>(properties.keySet()))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build())
        .strict(true)
        .eagerInputStreaming(true)
        .build();
  }

  private static Map<String, Object> props(Object... keyValues) {
    Map<String, Object> rv = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      rv.put((String) keyValues[i], keyValues[i + 1]);
    }
    return rv;
  }

  private static Map<String, Object> str(String description) {
    return props("type", "string", "description", description);
  }

  private static Map<String, Object> num(String description) {
    return props("type", "number", "description", description);
  }

  private static Map<String, Object> integer(String description) {
    return props("type", "integer", "description", description);
  }

  private static Map<String, Object> enm(String description, String... values) {
    return props("type", "string", "description", description, "enum", List.of(values));
  }

  private static Map<String, Object> arr(Map<String, Object> items, String description) {
    return props("type", "array", "description", description, "items", items);
  }

  private static Map<String, Object> obj(Map<String, Object> properties) {
    return props("type", "object", "properties", properties, "required", new ArrayList<>(properties.keySet()), "additionalProperties", false);
  }

  private static Map<String, Object> pair() {
    return obj(props("name", str("The parameter or detail name."), "value", str("The value as text, like FORWARD, 1.5, hello or dog.")));
  }
}
