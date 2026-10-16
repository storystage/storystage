/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.lgna.croquet.SimpleComposite;
import org.lgna.croquet.views.BorderPanel;
import org.lgna.croquet.views.SwingAdapter;
import org.storystage.prompter.key.KeyStore;
import org.storystage.prompter.key.KeyStores;
import org.storystage.prompter.settings.PrompterEnabledState;
import org.storystage.prompter.settings.PrompterPolicy;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.UUID;

/**
 * The Prompter panel beside the editors. This version shows the Prompter's status and the key
 * controls; the conversation, input box and controls come with WP9. See docs/spec/04-ux.md §2.
 */
public final class PrompterPanelComposite extends SimpleComposite<BorderPanel> {
  private final PrompterPolicy policy;
  private final JEditorPane status = new JEditorPane("text/html", "");
  private final JButton setUpButton = new JButton("Set up again...");
  private final JButton removeKeyButton = new JButton("Remove key");

  public PrompterPanelComposite(PrompterPolicy policy) {
    super(UUID.fromString("e2b4c6d8-0a1c-4e3f-8b5d-7f9a1c3e5b70"));
    this.policy = policy;
    this.status.setEditable(false);
    this.status.setOpaque(false);
    this.status.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    this.setUpButton.addActionListener(e -> {
      SetupWizard.showWizard(this.status);
      this.refresh();
    });
    this.removeKeyButton.addActionListener(e -> this.removeKey());
  }

  @Override
  protected BorderPanel createView() {
    JPanel content = new JPanel(new BorderLayout(0, 8));
    content.add(this.status, BorderLayout.PAGE_START);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    buttons.add(this.setUpButton);
    buttons.add(this.removeKeyButton);
    JPanel south = new JPanel();
    south.setLayout(new BoxLayout(south, BoxLayout.PAGE_AXIS));
    south.add(buttons);
    content.add(south, BorderLayout.CENTER);
    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    BorderPanel rv = new BorderPanel(this);
    rv.addPageStartComponent(new SwingAdapter(content));
    return rv;
  }

  @Override
  public void handlePreActivation() {
    super.handlePreActivation();
    this.refresh();
  }

  void refresh() {
    KeyStore store = KeyStores.get();
    boolean hasKey = store.read().isPresent();
    StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif'><b>Prompter</b><p>");
    if (hasKey) {
      sb.append("Ready. Your Claude key is kept in ").append(store.describe()).append(".</p>");
    } else {
      sb.append("Set up the Prompter to use it with your own Claude account.</p>");
    }
    if (this.policy.isExplainOnly()) {
      sb.append("<p>Your school has set the Prompter to explain only.</p>");
    }
    sb.append("<p>Building scenes from your requests arrives in the next StoryStage builds.</p></body></html>");
    this.status.setText(sb.toString());
    this.setUpButton.setText(hasKey ? "Set up again..." : "Set up...");
    this.removeKeyButton.setEnabled(hasKey);
  }

  private void removeKey() {
    int answer = JOptionPane.showConfirmDialog(this.status, "Remove your Claude key from this computer and switch the Prompter off?", "Remove key", JOptionPane.OK_CANCEL_OPTION);
    if (answer != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      KeyStores.get().delete();
    } catch (KeyStore.KeyStoreException e) {
      JOptionPane.showMessageDialog(this.status, e.getMessage(), "Remove key", JOptionPane.WARNING_MESSAGE);
    }
    PrompterEnabledState.getInstance().setValueTransactionlessly(false);
    this.refresh();
  }
}
