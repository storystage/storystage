/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

import java.util.Optional;

/**
 * Where the user's Claude API key is kept. Implementations never log the key or write it to
 * preferences, projects or plain files. See docs/spec/02-keys-privacy-switches.md §2.
 */
public interface KeyStore {
  /** The stored key, if any. */
  Optional<String> read();

  /** Stores the key, replacing any existing one. */
  void write(String key) throws KeyStoreException;

  /** Removes the stored key; does nothing if there is none. */
  void delete() throws KeyStoreException;

  /** A short description for the About box, for example "Windows Credential Manager". */
  String describe();

  /** Thrown when the store cannot be used. The message never contains the key. */
  final class KeyStoreException extends Exception {
    public KeyStoreException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
