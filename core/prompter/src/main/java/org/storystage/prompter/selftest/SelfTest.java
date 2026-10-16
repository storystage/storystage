/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.selftest;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import edu.cmu.cs.dennisc.render.gl.RendererNativeLibraryLoader;
import org.alice.ide.IDE;
import org.alice.ide.ProjectDocument;
import org.alice.ide.project.ProjectDocumentState;
import org.alice.stageide.StageIDE;
import org.alice.stageide.ast.BootstrapUtilities;
import org.alice.stageide.openprojectpane.models.TemplateUriState;
import org.lgna.croquet.Application;
import org.lgna.croquet.undo.UndoHistory;
import org.lgna.project.Project;
import org.lgna.project.ast.NamedUserType;
import org.lgna.project.ast.UserMethod;
import org.lgna.project.io.IoUtilities;
import org.storystage.prompter.claude.Json;
import org.storystage.prompter.claude.SystemPrompt;
import org.storystage.prompter.claude.ToolExecutor;
import org.storystage.prompter.context.ApiCatalog;
import org.storystage.prompter.context.NodeIds;
import org.storystage.prompter.context.ProjectText;
import org.storystage.prompter.tools.GalleryCatalog;
import org.storystage.prompter.tools.ToolSchemas;
import org.storystage.prompter.tools.WorldTools;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * Starts StoryStage on a new Grass world and runs the Prompter's tools against the live scene,
 * without Claude: adding a model, changing it, adding blocks and events, reading the project,
 * then undoing it all. Prints "SELFTEST PASS" or "SELFTEST FAIL" and exits with 0 or 1.
 * Run by CI (StoryStage-selftest.exe) and useful on a learner's PC to check an install.
 */
public final class SelfTest {
  private static final long START_TIMEOUT_MS = 240_000;
  private final List<String> failures = new ArrayList<>();
  private int checks;

  private SelfTest() {
  }

  public static void main(String[] args) throws Exception {
    // A previous killed run must not stop this one with Alice's "did not close" message.
    System.setProperty("org.alice.stageide.isCrashDetectionDesired", "false");
    if ("true".equalsIgnoreCase(System.getenv("CI"))) {
      // On a CI machine nobody can click the licence prompts. Elsewhere they show as usual.
      Preferences.userRoot().node("/org/lgna/project").putBoolean("isLicenseAccepted", true);
      Preferences.userRoot().node("/edu/cmu/cs/dennisc/nebulous").putBoolean("isLicenseAccepted", true);
    }
    RendererNativeLibraryLoader.initializeIfNecessary();
    File world = File.createTempFile("storystage-selftest", ".a3p");
    world.deleteOnExit();
    TemplateUriState.Template grass = TemplateUriState.Template.GRASS;
    NamedUserType programType = BootstrapUtilities.createProgramType(grass.getSurfaceAppearance(), grass.getAtmospherColor(), grass.getFogDensity(),
        grass.getAboveLightColor(), grass.getBelowLightColor(), grass.getGroundOpacity(), false);
    IoUtilities.writeProject(world, new Project(programType, Project.SceneCameraType.WindowCamera));
    log("world written to " + world);

    Thread runner = new Thread(() -> {
      int code;
      try {
        code = new SelfTest().run();
      } catch (Throwable t) {
        t.printStackTrace();
        log("SELFTEST FAIL: " + t);
        code = 1;
      }
      Runtime.getRuntime().halt(code);
    }, "StoryStage self-test");
    runner.setDaemon(true);
    runner.start();
    Class.forName("org.alice.stageide.EntryPoint").getMethod("main", String[].class).invoke(null, (Object) new String[] {world.getAbsolutePath()});
  }

  private static void log(String line) {
    System.out.println("[selftest] " + line);
    System.out.flush();
  }

  private int run() throws Exception {
    waitForWorld();
    log("world open");
    NodeIds ids = new NodeIds();
    GalleryCatalog gallery = GalleryCatalog.load();
    this.check("gallery has models", gallery.ids().size() > 10, gallery.ids().size() + " models");
    log("first gallery ids: " + gallery.ids().subList(0, Math.min(5, gallery.ids().size())));
    if (gallery.isEmpty()) {
      throw new IllegalStateException("The gallery catalog is empty, so nothing can be added.");
    }
    String prompt = SystemPrompt.build(ApiCatalog.render(), gallery.render());
    this.check("system prompt built", prompt.length() > 5_000, prompt.length() + " characters");

    WorldTools tools = new WorldTools(ids, gallery, false);
    String model = pickModel(gallery);
    log("using gallery model " + model);
    int historyBefore = historySize();
    int fieldsBefore = onEdt(() -> sceneType().fields.size());
    int statementsBefore = onEdt(SelfTest::myFirstMethodLength);

    this.ok(tools, ToolSchemas.ADD_MODEL, Map.of("gallery_item", model, "name", "star", "position", Map.of("x", 1, "y", 0, "z", 1), "turn_degrees", 0, "narration", "A star appears."));
    this.check("model added to the scene", onEdt(() -> sceneType().fields.size()) == fieldsBefore + 1, null);
    this.ok(tools, ToolSchemas.SET_PROPERTY, Map.of("object", "star", "property", "position", "value", "-1, 0, 2", "narration", "It moves."));
    this.ok(tools, ToolSchemas.SET_PROPERTY, Map.of("object", "star", "property", "turn_degrees", "value", "45", "narration", "It turns."));
    this.ok(tools, ToolSchemas.SET_PROPERTY, Map.of("object", "star", "property", "opacity", "value", "0.5", "narration", "It fades."));
    this.ok(tools, ToolSchemas.SET_PROPERTY, Map.of("object", "star", "property", "paint", "value", "RED", "narration", "It turns red."));

    String loop = this.block(this.ok(tools, ToolSchemas.INSERT_CONTROL, Map.of("method", "Scene.myFirstMethod", "parent_block", "", "position", -1, "kind", "count", "count", 3, "narration", "Repeat three times.")));
    this.ok(tools, ToolSchemas.INSERT_STATEMENT, statement("", loop, "star", "move", List.of(pair("direction", "FORWARD"), pair("amount", "1")), List.of(pair("duration", "0.5"))));
    String together = this.block(this.ok(tools, ToolSchemas.INSERT_CONTROL, Map.of("method", "Scene.myFirstMethod", "parent_block", "", "position", -1, "kind", "do_together", "count", 0, "narration", "At the same time.")));
    this.ok(tools, ToolSchemas.INSERT_STATEMENT, statement("", together, "star", "say", List.of(pair("text", "Hello")), List.of()));
    this.ok(tools, ToolSchemas.INSERT_STATEMENT, statement("", together, "star", "turn", List.of(pair("direction", "LEFT"), pair("amount", "0.25")), List.of()));
    this.ok(tools, ToolSchemas.INSERT_STATEMENT, statement("Scene.myFirstMethod", "", "camera", "pointAt", List.of(pair("target", "star")), List.of()));

    String click = this.block(this.ok(tools, ToolSchemas.ADD_EVENT_LISTENER, event("mouse_click_on_object", List.of("star"), List.of(), -1)));
    this.ok(tools, ToolSchemas.INSERT_STATEMENT, statement("", click, "star", "say", List.of(pair("text", "Ouch")), List.of()));
    this.ok(tools, ToolSchemas.ADD_EVENT_LISTENER, event("move_with_arrow_keys", List.of("star"), List.of(), -1));
    this.ok(tools, ToolSchemas.ADD_EVENT_LISTENER, event("proximity_enter", List.of("star"), List.of("camera"), 2));
    this.ok(tools, ToolSchemas.ADD_EVENT_LISTENER, event("time", List.of(), List.of(), 2));

    this.failsWith(tools, ToolSchemas.INSERT_STATEMENT, statement("Scene.myFirstMethod", "", "star", "noSuchBlock", List.of(), List.of()), "unknown_method");
    this.failsWith(tools, ToolSchemas.ADD_MODEL, Map.of("gallery_item", "no/such/model", "name", "x", "position", Map.of("x", 0, "y", 0, "z", 0), "turn_degrees", 0, "narration", ""), "");
    this.failsWith(tools, ToolSchemas.SET_PROPERTY, Map.of("object", "nobody", "property", "opacity", "value", "1", "narration", ""), "");

    String text = onEdt(() -> ProjectText.render(IDE.getActiveInstance().getProject(), ids));
    this.check("project text has ids", text.contains("/*#"), null);
    this.check("project text has the click event", text.contains("addMouseClickOnObjectListener") && text.contains("Ouch"), null);
    this.check("project text has the loop", text.contains("star.move"), null);
    log("project text: " + text.length() + " characters");

    int edits = tools.getCommittedEdits().size();
    this.check("every change is in Project History", historySize() == historyBefore + edits, (historySize() - historyBefore) + " of " + edits);
    onEdt(() -> {
      UndoHistory history = history();
      for (int i = 0; i < edits; i++) {
        history.performUndo();
      }
      return null;
    });
    this.check("undo removes the model", onEdt(() -> sceneType().fields.size()) == fieldsBefore, null);
    this.check("undo restores myFirstMethod", onEdt(SelfTest::myFirstMethodLength) == statementsBefore, null);

    log(this.checks + " checks, " + this.failures.size() + " failed");
    if (this.failures.isEmpty()) {
      log("SELFTEST PASS");
      return 0;
    }
    for (String f : this.failures) {
      log("failed: " + f);
    }
    log("SELFTEST FAIL");
    return 1;
  }

  private static void waitForWorld() throws Exception {
    long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      boolean ready = onEdt(() -> {
        StageIDE ide = StageIDE.getActiveInstance();
        return ide != null && ide.getProject() != null && ide.getSceneEditor() != null && ide.getSceneEditor().getActiveSceneInstance() != null;
      });
      if (ready) {
        Thread.sleep(3_000);
        return;
      }
      Thread.sleep(1_000);
    }
    throw new IllegalStateException("The world didn't open within " + START_TIMEOUT_MS / 1000 + " seconds.");
  }

  private static String pickModel(GalleryCatalog gallery) {
    for (String preferred : new String[] {"penguin", "Penguin", "cat", "Cat"}) {
      for (String id : gallery.ids()) {
        if (id.endsWith(preferred) || id.contains("/" + preferred)) {
          return id;
        }
      }
    }
    return gallery.ids().get(0);
  }

  private JsonNode ok(WorldTools tools, String tool, Map<String, ?> input) {
    ToolExecutor.Result result = tools.execute(tool, JsonValue.from(input));
    this.check(tool + " " + input.get("narration"), result.isOk(), result.getJson());
    try {
      return Json.MAPPER.readTree(result.getJson());
    } catch (IOException e) {
      return Json.MAPPER.createObjectNode();
    }
  }

  private void failsWith(WorldTools tools, String tool, Map<String, ?> input, String code) {
    ToolExecutor.Result result = tools.execute(tool, JsonValue.from(input));
    this.check(tool + " refuses a bad request", !result.isOk() && result.getJson().contains(code), result.getJson());
  }

  private String block(JsonNode result) {
    return result.path("ids").path("block").asText("");
  }

  private void check(String name, boolean passed, String detail) {
    this.checks++;
    log((passed ? "ok   " : "FAIL ") + name + (detail != null && !passed ? " -> " + detail : ""));
    if (!passed) {
      this.failures.add(name + (detail != null ? " (" + detail + ")" : ""));
    }
  }

  private static Map<String, Object> statement(String method, String parent, String target, String procedure, List<Map<String, String>> arguments, List<Map<String, String>> details) {
    return Map.of("method", method, "parent_block", parent, "position", -1, "target", target, "procedure", procedure,
        "arguments", arguments, "details", details, "narration", target + " " + procedure);
  }

  private static Map<String, Object> event(String kind, List<String> objects, List<String> others, double number) {
    return Map.of("kind", kind, "objects", objects, "other_objects", others, "number", number, "narration", kind);
  }

  private static Map<String, String> pair(String name, String value) {
    return Map.of("name", name, "value", value);
  }

  private static int myFirstMethodLength() {
    UserMethod m = sceneType().getDeclaredMethod("myFirstMethod");
    return m.body.getValue().statements.size();
  }

  private static NamedUserType sceneType() {
    return StageIDE.getActiveInstance().getSceneType();
  }

  private static UndoHistory history() {
    ProjectDocument document = ProjectDocumentState.getInstance().getValue();
    return document.getUndoHistory(Application.PROJECT_GROUP);
  }

  private static int historySize() throws Exception {
    return onEdt(() -> history().getInsertionIndex());
  }

  private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        value.set(work.call());
      } catch (Exception e) {
        error.set(e);
      }
    });
    if (error.get() != null) {
      throw error.get();
    }
    return value.get();
  }
}
