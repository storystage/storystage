/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

import java.util.Optional;

/**
 * The operating system's credential store: Windows Credential Manager, macOS Keychain or the
 * Linux Secret Service, through the java-keyring library.
 */
public final class KeyringKeyStore implements KeyStore {
  static final String SERVICE = "StoryStage";
  static final String ACCOUNT = "anthropic-api-key";
  private final Keyring keyring;

  private KeyringKeyStore(Keyring keyring) {
    this.keyring = keyring;
  }

  /** The OS store, or empty if this computer doesn't offer one. */
  public static Optional<KeyStore> create() {
    try {
      return Optional.of(new KeyringKeyStore(Keyring.create()));
    } catch (BackendNotSupportedException | RuntimeException | LinkageError e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> read() {
    try {
      String key = this.keyring.getPassword(SERVICE, ACCOUNT);
      return key == null || key.isEmpty() ? Optional.empty() : Optional.of(key);
    } catch (PasswordAccessException | RuntimeException e) {
      // Not found is reported as an exception by some back ends.
      return Optional.empty();
    }
  }

  @Override
  public void write(String key) throws KeyStoreException {
    try {
      this.keyring.setPassword(SERVICE, ACCOUNT, key);
    } catch (PasswordAccessException | RuntimeException e) {
      throw new KeyStoreException("Couldn't save the key in " + this.describe() + ".", null);
    }
  }

  @Override
  public void delete() throws KeyStoreException {
    if (this.read().isEmpty()) {
      return;
    }
    try {
      this.keyring.deletePassword(SERVICE, ACCOUNT);
    } catch (PasswordAccessException | RuntimeException e) {
      throw new KeyStoreException("Couldn't remove the key from " + this.describe() + ".", null);
    }
  }

  @Override
  public String describe() {
    switch (this.keyring.getKeyringStorageType()) {
    case WINDOWS_CREDENTIAL_STORE:
      return "Windows Credential Manager";
    case OSX_KEYCHAIN:
    case LEGACY_OSX_KEYCHAIN:
      return "macOS Keychain";
    case GNOME_KEYRING:
      return "GNOME Keyring";
    case KWALLET:
      return "KWallet";
    default:
      return "the system credential store";
    }
  }

  @Override
  public String toString() {
    return "KeyringKeyStore[" + this.describe() + "]";
  }
}
