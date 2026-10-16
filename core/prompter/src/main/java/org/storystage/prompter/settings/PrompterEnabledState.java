/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import org.lgna.croquet.Application;
import org.lgna.croquet.preferences.PreferenceBooleanState;

import java.util.UUID;

/** Prompter is on or off. Off by default; turned on by completing set-up. See docs/spec/02-keys-privacy-switches.md §4. */
public final class PrompterEnabledState extends PreferenceBooleanState {
  private static class SingletonHolder {
    private static PrompterEnabledState instance = new PrompterEnabledState();
  }

  public static PrompterEnabledState getInstance() {
    return SingletonHolder.instance;
  }

  private PrompterEnabledState() {
    super(Application.DOCUMENT_UI_GROUP, UUID.fromString("8d6c2f3e-1b7a-4c5d-9e8f-0a1b2c3d4e5f"), false);
  }
}
