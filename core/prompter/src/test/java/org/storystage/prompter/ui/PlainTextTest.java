/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlainTextTest {
  @Test
  public void boldMarksGo() {
    assertEquals("I added a penguin called penguin.", PlainText.of("I added a penguin called **penguin**."));
  }

  @Test
  public void boldSplitAcrossPiecesGoes() {
    PlainText filter = new PlainText();
    String shown = filter.feed("press *") + filter.feed("*Rehearse*") + filter.feed("*, it") + filter.flush();
    assertEquals("press Rehearse, it", shown);
  }

  @Test
  public void headingMarksGo() {
    assertEquals("Your scene\n- a cow", PlainText.of("## Your scene\n- a cow"));
  }

  @Test
  public void singleStarsAndHashesInTextStay() {
    assertEquals("2 * 3 is 6, item #4", PlainText.of("2 * 3 is 6, item #4"));
  }
}
