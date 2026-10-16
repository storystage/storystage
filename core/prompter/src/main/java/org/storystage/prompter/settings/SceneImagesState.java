/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.settings;

import org.lgna.croquet.Application;
import org.lgna.croquet.preferences.PreferenceBooleanState;

import java.util.UUID;

/** A small picture of the scene sent with each request. Off by default. See docs/spec/02-keys-privacy-switches.md §4. */
public final class SceneImagesState extends PreferenceBooleanState {
  private static class SingletonHolder {
    private static SceneImagesState instance = new SceneImagesState();
  }

  public static SceneImagesState getInstance() {
    return SingletonHolder.instance;
  }

  private SceneImagesState() {
    super(Application.DOCUMENT_UI_GROUP, UUID.fromString("9e3c1a7b-5d2f-4b8e-a6c4-1f7d3b9e5a20"), false);
  }
}
