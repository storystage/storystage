/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter;

import edu.cmu.cs.dennisc.java.util.logging.Logger;
import org.lgna.croquet.Composite;
import org.lgna.croquet.StandardMenuItemPrepModel;
import org.storystage.prompter.settings.PrompterEnabledState;
import org.storystage.prompter.settings.PrompterPolicy;
import org.storystage.prompter.settings.SpokenStepsState;
import org.storystage.prompter.settings.VoiceInputState;
import org.storystage.prompter.spi.PrompterProvider;
import org.storystage.prompter.ui.PrompterPanelComposite;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Entry point of the Prompter module, found by the IDE through {@link java.util.ServiceLoader}. */
public final class PrompterModule implements PrompterProvider {
  private final PrompterPolicy policy;
  private final List<Runnable> panelShowingListeners = new CopyOnWriteArrayList<>();

  public PrompterModule() {
    this.policy = PrompterPolicy.load();
    Logger.outln("StoryStage Prompter:", this.policy);
    PrompterEnabledState.getInstance().addNewSchoolValueListener(e -> this.firePanelShowingChanged());
    if (this.policy.isLockedOff()) {
      PrompterEnabledState.getInstance().setEnabled(false);
      VoiceInputState.getInstance().setEnabled(false);
      SpokenStepsState.getInstance().setEnabled(false);
    } else if (!this.policy.isVoiceInputAllowed()) {
      VoiceInputState.getInstance().setEnabled(false);
    }
  }

  public PrompterPolicy getPolicy() {
    return this.policy;
  }

  @Override
  public Composite<?> createPanel() {
    return new PrompterPanelComposite(this.policy);
  }

  @Override
  public boolean isPanelShowing() {
    return !this.policy.isLockedOff() && PrompterEnabledState.getInstance().getValue();
  }

  @Override
  public void addPanelShowingListener(Runnable listener) {
    this.panelShowingListeners.add(listener);
  }

  @Override
  public List<StandardMenuItemPrepModel> getPreferenceMenuItems() {
    return Arrays.asList(
        PrompterEnabledState.getInstance().getMenuItemPrepModel(),
        VoiceInputState.getInstance().getMenuItemPrepModel(),
        SpokenStepsState.getInstance().getMenuItemPrepModel());
  }

  private void firePanelShowingChanged() {
    for (Runnable listener : this.panelShowingListeners) {
      listener.run();
    }
  }
}
