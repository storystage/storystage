/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.lgna.croquet.SimpleComposite;
import org.lgna.croquet.views.BorderPanel;
import org.lgna.croquet.views.HtmlMultiLineLabel;
import org.storystage.prompter.settings.PrompterPolicy;

import javax.swing.BorderFactory;
import java.util.UUID;

/**
 * The Prompter panel beside the editors. This first version shows the Prompter's status; the
 * conversation, input box and controls come with WP9. See docs/spec/04-ux.md §2.
 */
public final class PrompterPanelComposite extends SimpleComposite<BorderPanel> {
  private final PrompterPolicy policy;

  public PrompterPanelComposite(PrompterPolicy policy) {
    super(UUID.fromString("e2b4c6d8-0a1c-4e3f-8b5d-7f9a1c3e5b70"));
    this.policy = policy;
  }

  @Override
  protected BorderPanel createView() {
    StringBuilder sb = new StringBuilder();
    sb.append("<strong>Prompter</strong><p>");
    if (this.policy.isExplainOnly()) {
      sb.append("Your school has set the Prompter to explain only.<p>");
    }
    sb.append("Set up the Prompter to use it with your own Claude account. Set-up arrives in the next StoryStage build.");
    HtmlMultiLineLabel label = new HtmlMultiLineLabel(sb.toString());
    BorderPanel rv = new BorderPanel(this);
    rv.addPageStartComponent(label);
    rv.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return rv;
  }
}
