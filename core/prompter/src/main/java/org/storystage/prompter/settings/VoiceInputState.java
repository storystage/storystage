/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import org.lgna.croquet.Application;
import org.lgna.croquet.preferences.PreferenceBooleanState;

import java.util.UUID;

/** Voice input (hold to talk). See docs/spec/02-keys-privacy-switches.md §4. */
public final class VoiceInputState extends PreferenceBooleanState {
  private static class SingletonHolder {
    private static VoiceInputState instance = new VoiceInputState();
  }

  public static VoiceInputState getInstance() {
    return SingletonHolder.instance;
  }

  private VoiceInputState() {
    super(Application.DOCUMENT_UI_GROUP, UUID.fromString("5b2e9c41-7d3a-4f6e-8a1b-2c3d4e5f6a7b"), false);
  }
}
