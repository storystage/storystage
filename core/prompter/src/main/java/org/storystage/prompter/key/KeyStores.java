/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

/** Chooses where the key is kept on this computer. */
public final class KeyStores {
  private static KeyStore instance;

  private KeyStores() {
    throw new AssertionError();
  }

  /** The OS credential store when available, otherwise a session-only store. */
  public static synchronized KeyStore get() {
    if (instance == null) {
      instance = KeyringKeyStore.create().orElseGet(MemoryKeyStore::new);
    }
    return instance;
  }
}
