/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prefs;

import java.io.File;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Keeps StoryStage's preferences in its own folder instead of the Java preferences that Alice 3
 * also uses (on Windows, one registry branch keyed by package name). Without this, StoryStage and
 * an installed Alice 3 share licence acceptance, recent projects, crash markers and window layout
 * (found in TR1). Selected by the launchers with
 * {@code -Djava.util.prefs.PreferencesFactory=org.storystage.prefs.FilePreferencesFactory};
 * {@code -Dstorystage.preferences.dir=...} overrides the folder.
 */
public final class FilePreferencesFactory implements PreferencesFactory {
  private static Preferences userRoot;
  private static Preferences systemRoot;

  @Override
  public synchronized Preferences userRoot() {
    if (userRoot == null) {
      userRoot = new FilePreferences(null, "", new File(baseDirectory(), "user"));
    }
    return userRoot;
  }

  @Override
  public synchronized Preferences systemRoot() {
    // StoryStage has no machine-wide preferences; keep them beside the user ones.
    if (systemRoot == null) {
      systemRoot = new FilePreferences(null, "", new File(baseDirectory(), "system"));
    }
    return systemRoot;
  }

  static File baseDirectory() {
    String override = System.getProperty("storystage.preferences.dir");
    if (override != null && !override.isEmpty()) {
      return new File(override);
    }
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String home = System.getProperty("user.home");
    if (os.startsWith("windows")) {
      String appData = System.getenv("APPDATA");
      return Paths.get(appData != null ? appData : home, "StoryStage", "preferences").toFile();
    } else if (os.startsWith("mac")) {
      return Paths.get(home, "Library", "Preferences", "StoryStage").toFile();
    } else {
      return Paths.get(home, ".config", "StoryStage", "preferences").toFile();
    }
  }
}
