/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.spi;

import org.lgna.croquet.Composite;
import org.lgna.croquet.StandardMenuItemPrepModel;

import java.util.List;

/**
 * The StoryStage Prompter, as seen by the IDE. The implementation lives in the optional
 * {@code core/prompter} module and is found with {@link java.util.ServiceLoader}; without it the
 * IDE behaves exactly like Alice. See docs/spec/00-architecture.md.
 */
public interface PrompterProvider {
  /** Creates the panel shown beside the editors. Called once, on the Swing thread. */
  Composite<?> createPanel();

  /** Whether the panel should be showing now: the Prompter is on and not locked off. */
  boolean isPanelShowing();

  /** Registers a listener called on the Swing thread when {@link #isPanelShowing()} may have changed. */
  void addPanelShowingListener(Runnable listener);

  /** Items appended to the Preferences menu (may be empty). */
  List<StandardMenuItemPrepModel> getPreferenceMenuItems();
}
