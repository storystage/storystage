/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.key;

import java.util.regex.Pattern;

/** Rules for handling Claude API keys. See docs/spec/02-keys-privacy-switches.md §2. */
public final class ApiKeys {
  /** Anything that looks like an Anthropic key, for scrubbing text. */
  public static final Pattern KEY_PATTERN = Pattern.compile("sk-ant-[A-Za-z0-9_\\-]{20,}");
  public static final String MASK = "sk-ant-(hidden)";

  private ApiKeys() {
    throw new AssertionError();
  }

  /** Removes surrounding spaces and quotes that often come with a pasted key. */
  public static String clean(String pasted) {
    if (pasted == null) {
      return "";
    }
    String s = pasted.trim();
    if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
      s = s.substring(1, s.length() - 1).trim();
    }
    return s;
  }

  /** A quick shape check before contacting Anthropic; the real check is {@code KeyCheck}. */
  public static boolean looksLikeKey(String key) {
    return key != null && KEY_PATTERN.matcher(key).matches();
  }

  /** Replaces every key-like string in a text with {@link #MASK}. */
  public static String scrub(String text) {
    return text == null ? null : KEY_PATTERN.matcher(text).replaceAll(MASK);
  }
}
