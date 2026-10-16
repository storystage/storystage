/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.spi;

import edu.cmu.cs.dennisc.java.util.logging.Logger;
import org.lgna.croquet.Composite;
import org.lgna.croquet.StandardMenuItemPrepModel;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;

/** Finds the optional Prompter module and connects it to the IDE. */
public final class Prompters {
  private static Optional<PrompterProvider> provider;

  private Prompters() {
    throw new AssertionError();
  }

  /** The Prompter, if the {@code core/prompter} module is on the classpath. */
  public static synchronized Optional<PrompterProvider> getProvider() {
    if (provider == null) {
      Optional<PrompterProvider> found = Optional.empty();
      try {
        found = ServiceLoader.load(PrompterProvider.class).findFirst();
      } catch (Throwable t) {
        Logger.throwable(t, "StoryStage Prompter could not be loaded; continuing without it.");
      }
      provider = found;
    }
    return provider;
  }

  /**
   * Returns the editor unchanged when there is no Prompter, or a split with the Prompter panel on
   * the right.
   */
  public static Composite<?> wrapWithPanel(UUID slotId, Composite<?> editor) {
    Optional<PrompterProvider> p = getProvider();
    if (p.isPresent()) {
      return new PrompterSlotComposite(slotId, editor, p.get());
    } else {
      return editor;
    }
  }

  /** Items the Prompter adds to the Preferences menu; empty without the module. */
  public static List<StandardMenuItemPrepModel> getPreferenceMenuItems() {
    return getProvider().map(PrompterProvider::getPreferenceMenuItems).orElse(Collections.emptyList());
  }
}
