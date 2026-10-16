/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoicesTest {
  @Test
  public void sanitiseMakesOneSafeLine() {
    assertEquals("Adding a cat. Then a dog.", Voices.sanitise("  Adding a \"cat\".\nThen a `dog`.  "));
    assertEquals("", Voices.sanitise(null));
    assertFalse(Voices.sanitise("cost $5").contains("$"));
    assertTrue(Voices.sanitise("x".repeat(1000)).length() <= Voices.MAX_LENGTH);
  }

  @Test
  public void windowsGetsWindowsSpeech() {
    assertEquals("Windows speech", Voices.choose("Windows 11").name());
  }
}
