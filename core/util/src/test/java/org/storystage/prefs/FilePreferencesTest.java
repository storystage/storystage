/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prefs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilePreferencesTest {
  @TempDir
  File folder;

  private Preferences root() {
    return new FilePreferences(null, "", this.folder);
  }

  @Test
  public void valuesSurviveANewRoot() throws Exception {
    root().node("org/lgna/project").putBoolean("isLicenseAccepted", true);
    root().node("org/alice/stageide").putByteArray("blob", new byte[] {1, 2, 3});
    assertTrue(root().node("org/lgna/project").getBoolean("isLicenseAccepted", false));
    assertArrayEquals(new byte[] {1, 2, 3}, root().node("org/alice/stageide").getByteArray("blob", new byte[0]));
  }

  @Test
  public void childNamesAndOddCharactersRoundTrip() throws Exception {
    root().node("odd name/with:colon").put("k", "v=1 # x");
    assertEquals("v=1 # x", root().node("odd name/with:colon").get("k", "?"));
    String[] children = root().childrenNames();
    Arrays.sort(children);
    assertArrayEquals(new String[] {"odd name"}, children);
  }

  @Test
  public void removedValuesAndNodesGo() throws Exception {
    Preferences node = root().node("a/b");
    node.put("k", "v");
    node.remove("k");
    assertEquals("?", root().node("a/b").get("k", "?"));
    root().node("a/b").removeNode();
    assertFalse(root().node("a").nodeExists("b"));
  }
}
