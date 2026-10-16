/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Machine-wide settings a school or exam centre can deploy to switch the Prompter off or limit
 * it. They override the user's own preferences. See docs/spec/02-keys-privacy-switches.md §4.
 *
 * <p>Policy file keys: {@code prompter.enabled}, {@code prompter.explainOnly},
 * {@code prompter.voiceInput}. A JVM option {@code -Dstorystage.prompter.enabled=false} also
 * switches the Prompter off. A file that cannot be read or parsed switches the Prompter off.
 */
public final class PrompterPolicy {
  public static final String SYSTEM_PROPERTY = "storystage.prompter.enabled";
  public static final String KEY_ENABLED = "prompter.enabled";
  public static final String KEY_EXPLAIN_ONLY = "prompter.explainOnly";
  public static final String KEY_VOICE_INPUT = "prompter.voiceInput";
  private static final String FILE_NAME = "policy.properties";

  /** No policy: the user decides. */
  public static final PrompterPolicy NONE = new PrompterPolicy(false, false, true, "No policy");

  private final boolean lockedOff;
  private final boolean explainOnly;
  private final boolean voiceInputAllowed;
  private final String source;

  private PrompterPolicy(boolean lockedOff, boolean explainOnly, boolean voiceInputAllowed, String source) {
    this.lockedOff = lockedOff;
    this.explainOnly = explainOnly;
    this.voiceInputAllowed = voiceInputAllowed;
    this.source = source;
  }

  /** True when the Prompter must be off whatever the user chooses. */
  public boolean isLockedOff() {
    return this.lockedOff;
  }

  /** True when the Prompter may only explain, not change worlds. */
  public boolean isExplainOnly() {
    return this.explainOnly;
  }

  /** False when voice input is not allowed. */
  public boolean isVoiceInputAllowed() {
    return this.voiceInputAllowed;
  }

  /** Where the policy came from, for the About box and logs. */
  public String getSource() {
    return this.source;
  }

  /** Reads the policy for this computer: the JVM option first, then the policy file. */
  public static PrompterPolicy load() {
    if ("false".equalsIgnoreCase(System.getProperty(SYSTEM_PROPERTY))) {
      return new PrompterPolicy(true, false, false, "JVM option -D" + SYSTEM_PROPERTY + "=false");
    }
    Path path = defaultPolicyPath(System.getProperty("os.name", ""), System.getenv());
    return path != null ? fromFile(path) : NONE;
  }

  /** Reads a policy file; a missing file means no policy, an unreadable one means off. */
  public static PrompterPolicy fromFile(Path path) {
    if (!Files.exists(path)) {
      return NONE;
    }
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException | IllegalArgumentException e) {
      return new PrompterPolicy(true, false, false, "Unreadable policy file " + path + " (Prompter off)");
    }
    return fromProperties(properties, path.toString());
  }

  /** Builds a policy from file contents. Values other than true/false switch that setting off. */
  public static PrompterPolicy fromProperties(Properties properties, String source) {
    boolean enabled = parse(properties, KEY_ENABLED, true);
    boolean explainOnly = parse(properties, KEY_EXPLAIN_ONLY, false);
    boolean voiceInput = parse(properties, KEY_VOICE_INPUT, true);
    return new PrompterPolicy(!enabled, enabled && explainOnly, enabled && voiceInput, source);
  }

  private static boolean parse(Properties properties, String key, boolean defaultValue) {
    String value = properties.getProperty(key);
    if (value == null) {
      return defaultValue;
    }
    value = value.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(value)) {
      return true;
    } else if ("false".equals(value)) {
      return false;
    } else {
      // Unclear values err on the side of the restriction: enabled and voice off, explain-only on.
      return KEY_EXPLAIN_ONLY.equals(key);
    }
  }

  /** The machine-wide policy file location for an operating system, or null if unknown. */
  public static Path defaultPolicyPath(String osName, Map<String, String> env) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      String programData = env.get("PROGRAMDATA");
      return Paths.get(programData != null ? programData : "C:\\ProgramData", "StoryStage", FILE_NAME);
    } else if (os.startsWith("mac")) {
      return Paths.get("/Library/Application Support/StoryStage", FILE_NAME);
    } else if (os.contains("linux") || os.contains("nix") || os.contains("bsd")) {
      return Paths.get("/etc/storystage", FILE_NAME);
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    return "PrompterPolicy[lockedOff=" + this.lockedOff + ", explainOnly=" + this.explainOnly + ", voiceInputAllowed=" + this.voiceInputAllowed + ", source=" + this.source + "]";
  }
}
