/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import org.junit.Test;
import org.lgna.project.ast.BlockStatement;
import org.lgna.project.ast.Comment;
import org.lgna.project.ast.DoInOrder;
import org.lgna.project.ast.DoTogether;
import org.lgna.project.ast.Statement;
import org.lgna.project.ast.UserMethod;
import org.lgna.project.ast.UserParameter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ContextTest {
  @Test
  public void catalogHasPrimeTimeBlocksWithDetails() {
    String text = ApiCatalog.render();
    assertTrue("move is listed", text.contains("proc move("));
    assertTrue("move takes a direction", text.contains("MoveDirection"));
    assertTrue("details are listed", text.contains("duration?: Number"));
    assertTrue("values are listed", text.contains("MoveDirection: ") && text.contains("FORWARD"));
    assertTrue("functions are listed", text.contains("func "));
    // TUCKED_AWAY methods are left out, as in the block palette.
    assertFalse(text.contains("getPositionRelativeToVehicle("));
  }

  @Test
  public void catalogFitsItsTokenBudget() {
    int approxTokens = ApiCatalog.render().length() / 4;
    assertTrue("catalog is about " + approxTokens + " tokens", approxTokens < 45_000);
  }

  @Test
  public void idsAreShortUniqueAndReversible() {
    NodeIds ids = new NodeIds();
    Comment a = new Comment("a");
    Comment b = new Comment("b");
    String idA = ids.idOf(a);
    String idB = ids.idOf(b);
    assertEquals(idA, ids.idOf(a));
    assertFalse(idA.equals(idB));
    assertTrue(idA.length() >= 4);
    assertSame(a, ids.lookup(idA));
    assertSame(b, ids.lookup("#" + idB.toUpperCase()));
  }

  @Test
  public void projectTextTagsEveryStatement() {
    // Control blocks are tagged; comments are not (Claude never needs to target them).
    Statement inner = new DoInOrder(new BlockStatement(new Comment("wave")));
    DoTogether together = new DoTogether(new BlockStatement(inner));
    UserMethod method = new UserMethod("myFirstMethod", Void.TYPE, new UserParameter[0], new BlockStatement(together, new DoInOrder(new BlockStatement())));
    NodeIds ids = new NodeIds();
    String text = ProjectText.encode(method, ids);
    Matcher m = Pattern.compile("/\\*#([0-9a-f]+)\\*/").matcher(text);
    int count = 0;
    while (m.find()) {
      assertNotNull("id resolves: " + m.group(1), ids.lookup(m.group(1)));
      count++;
    }
    assertEquals(text, 3, count);
    assertSame(together, ids.lookup(ids.idOf(together)));
  }
}
