/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiKeysTest {
  /** A made-up key of the right shape. Not a real key. */
  static final String FAKE_KEY = "sk-ant-fake" + "0123456789abcdefghij";

  @Test
  public void cleanRemovesSpacesAndQuotes() {
    assertEquals(FAKE_KEY, ApiKeys.clean("  " + FAKE_KEY + "\n"));
    assertEquals(FAKE_KEY, ApiKeys.clean("\"" + FAKE_KEY + "\""));
    assertEquals(FAKE_KEY, ApiKeys.clean("'" + FAKE_KEY + "'"));
    assertEquals("", ApiKeys.clean(null));
  }

  @Test
  public void shapeCheck() {
    assertTrue(ApiKeys.looksLikeKey(FAKE_KEY));
    assertFalse(ApiKeys.looksLikeKey("sk-ant-short"));
    assertFalse(ApiKeys.looksLikeKey("hello"));
    assertFalse(ApiKeys.looksLikeKey(FAKE_KEY + " extra"));
    assertFalse(ApiKeys.looksLikeKey(null));
  }

  @Test
  public void scrubHidesKeysInText() {
    String text = "Request failed with key " + FAKE_KEY + " at 10:00";
    String scrubbed = ApiKeys.scrub(text);
    assertFalse(scrubbed.contains(FAKE_KEY));
    assertTrue(scrubbed.contains(ApiKeys.MASK));
  }

  @Test
  public void memoryStoreNeverPrintsTheKey() {
    MemoryKeyStore store = new MemoryKeyStore();
    store.write(FAKE_KEY);
    assertEquals(FAKE_KEY, store.read().get());
    assertFalse(store.toString().contains(FAKE_KEY));
    store.delete();
    assertFalse(store.read().isPresent());
  }
}
