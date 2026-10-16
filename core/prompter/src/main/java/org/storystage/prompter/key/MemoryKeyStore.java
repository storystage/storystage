/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

import java.util.Optional;

/**
 * Keeps the key for this session only. Used in tests, and when the computer has no credential
 * store (the user is asked for the key again next time).
 */
public final class MemoryKeyStore implements KeyStore {
  private volatile String key;

  @Override
  public Optional<String> read() {
    return Optional.ofNullable(this.key);
  }

  @Override
  public void write(String key) {
    this.key = key;
  }

  @Override
  public void delete() {
    this.key = null;
  }

  @Override
  public String describe() {
    return "this session only (no credential store available)";
  }

  @Override
  public String toString() {
    return "MemoryKeyStore[" + (this.key != null ? ApiKeys.MASK : "empty") + "]";
  }
}
