/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import org.lgna.croquet.Application;
import org.lgna.croquet.preferences.PreferenceBooleanState;

import java.util.UUID;

/** Steps read aloud as the Prompter works. See docs/spec/02-keys-privacy-switches.md §4. */
public final class SpokenStepsState extends PreferenceBooleanState {
  private static class SingletonHolder {
    private static SpokenStepsState instance = new SpokenStepsState();
  }

  public static SpokenStepsState getInstance() {
    return SingletonHolder.instance;
  }

  private SpokenStepsState() {
    super(Application.DOCUMENT_UI_GROUP, UUID.fromString("c4a7e2d9-3f1b-4e8c-9d2a-6b5c4d3e2f1a"), true);
  }
}
