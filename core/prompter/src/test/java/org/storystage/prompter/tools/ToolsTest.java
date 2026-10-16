/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import org.junit.Test;
import org.lgna.story.Color;
import org.lgna.story.MoveDirection;
import org.lgna.story.SMovableTurnable;
import org.lgna.story.SQuadruped;
import org.storystage.prompter.claude.ToolExecutor;
import org.storystage.prompter.context.NodeIds;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ToolsTest {
  @Test
  public void schemasAreStrictAndComplete() {
    List<Tool> all = ToolSchemas.tools(false);
    assertEquals(6, all.size());
    for (Tool t : all) {
      assertTrue(t.name() + " is strict", t.strict().orElse(false));
      assertTrue(t.name() + " streams its input", t.eagerInputStreaming().orElse(false));
      assertTrue(t.name() + " forbids extra fields", t.inputSchema()._additionalProperties().containsKey("additionalProperties"));
    }
    List<Tool> explainOnly = ToolSchemas.tools(true);
    assertEquals(2, explainOnly.size());
  }

  @Test
  public void constantsResolveByName() throws ValueConverter.BadValue {
    assertSame(MoveDirection.FORWARD, ValueConverter.constant(MoveDirection.class, "FORWARD"));
    assertSame(MoveDirection.FORWARD, ValueConverter.constant(MoveDirection.class, "MoveDirection.forward"));
    assertSame(Color.RED, ValueConverter.constant(Color.class, "red"));
    try {
      ValueConverter.constant(MoveDirection.class, "SIDEWAYS");
      fail("SIDEWAYS is not a MoveDirection");
    } catch (ValueConverter.BadValue expected) {
      assertTrue(expected.getMessage().contains("MoveDirection"));
    }
  }

  @Test
  public void numbersAreChecked() throws ValueConverter.BadValue {
    assertEquals(1.5, ValueConverter.parseNumber(" 1.5 "), 0);
    try {
      ValueConverter.parseNumber("far");
      fail();
    } catch (ValueConverter.BadValue expected) {
      assertTrue(expected.getMessage().contains("number"));
    }
  }

  @Test
  public void proceduresAreFoundByNameAndInputs() {
    Method move = WorldTools.findProcedure(SQuadruped.class, "move", 2);
    assertNotNull(move);
    assertEquals(SMovableTurnable.class, move.getDeclaringClass());
    assertEquals(null, WorldTools.findProcedure(SQuadruped.class, "noSuchBlock", 1));
    // TUCKED_AWAY methods are not offered.
    assertEquals(null, WorldTools.findProcedure(SQuadruped.class, "getPositionRelativeToVehicle", 0));
  }

  @Test
  public void explainOnlyRefusesChanges() {
    WorldTools tools = new WorldTools(new NodeIds(), GalleryCatalog.of(Collections.emptyMap()), true);
    ToolExecutor.Result result = tools.execute(ToolSchemas.ADD_MODEL, JsonValue.from(Map.of("gallery_item", "x")));
    assertFalse(result.isOk());
    assertTrue(result.getJson().contains("not_allowed_here"));
  }

  @Test
  public void learnersExplainOnlyChoiceRefusesChanges() {
    WorldTools tools = new WorldTools(new NodeIds(), GalleryCatalog.of(Collections.emptyMap()), false);
    tools.setRequestExplainOnly(true);
    ToolExecutor.Result result = tools.execute(ToolSchemas.SET_PROPERTY, JsonValue.from(Map.of("target", "x")));
    assertFalse(result.isOk());
    assertTrue(result.getJson().contains("chose Explain only"));
  }
}
