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
import org.storystage.prompter.settings.SpokenStepsState;
import org.storystage.prompter.speech.Voices;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.UUID;

/**
 * The Prompter panel beside the editors: the set-up card until a key is stored, then the
 * conversation. The key controls stay at the bottom. See docs/spec/04-ux.md §2–3.
 */
public final class PrompterPanelComposite extends SimpleComposite<BorderPanel> {
  private static final String SETUP = "setup";
  private static final String CHAT = "chat";

  private final PrompterPolicy policy;
  private final PrompterSession session;
  private final JEditorPane setupText = new JEditorPane("text/html", "");
  private final JButton setUpButton = new JButton("Set up...");
  private final JButton removeKeyButton = new JButton("Remove key");
  private final JButton privacyButton = new JButton("What is sent?");
  private final CardLayout cards = new CardLayout();
  private final JPanel cardPanel = new JPanel(this.cards);
  private PrompterChatPanel chat;

  public PrompterPanelComposite(PrompterPolicy policy) {
    super(UUID.fromString("e2b4c6d8-0a1c-4e3f-8b5d-7f9a1c3e5b70"));
    this.policy = policy;
    this.session = new PrompterSession(policy);
    this.setupText.setEditable(false);
    this.setupText.setOpaque(false);
    this.setupText.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    this.setUpButton.addActionListener(e -> {
      SetupWizard.showWizard(this.cardPanel);
      this.refresh();
    });
    this.removeKeyButton.addActionListener(e -> this.removeKey());
    this.privacyButton.addActionListener(e -> PrivacyNotice.show(this.cardPanel));
  }

  /** The chat card, once created, for connecting spoken steps. */
  PrompterChatPanel getChat() {
    return this.chat;
  }

  @Override
  protected BorderPanel createView() {
    JPanel setupCard = new JPanel(new BorderLayout());
    setupCard.add(this.setupText, BorderLayout.PAGE_START);
    setupCard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    this.chat = new PrompterChatPanel(this.session, this.policy);
    // Spoken steps (spec 03 §3): each narration is read aloud when the learner has chosen it.
    this.chat.setNarrationListener(text -> {
      if (SpokenStepsState.getInstance().getValue()) {
        Voices.get().speak(text);
      }
    });
    this.cardPanel.add(setupCard, SETUP);
    this.cardPanel.add(this.chat, CHAT);

    JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    keyRow.add(this.setUpButton);
    keyRow.add(this.removeKeyButton);
    JPanel privacyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    privacyRow.add(this.privacyButton);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(keyRow, BorderLayout.PAGE_START);
    bottom.add(privacyRow, BorderLayout.PAGE_END);

    JPanel content = new JPanel(new BorderLayout());
    content.add(this.cardPanel, BorderLayout.CENTER);
    content.add(bottom, BorderLayout.PAGE_END);
    BorderPanel rv = new BorderPanel(this);
    rv.addCenterComponent(new SwingAdapter(content));
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
    sb.append("Set up the Prompter to use it with your own Claude account.</p>");
    if (this.policy.isExplainOnly()) {
      sb.append("<p>Your school has set the Prompter to explain only.</p>");
    }
    sb.append("</body></html>");
    this.setupText.setText(sb.toString());
    this.cards.show(this.cardPanel, hasKey ? CHAT : SETUP);
    this.setUpButton.setText(hasKey ? "Set up again..." : "Set up...");
    this.setUpButton.setToolTipText(hasKey ? "Your key is kept in " + store.describe() + "." : null);
    this.removeKeyButton.setEnabled(hasKey);
  }

  private void removeKey() {
    int answer = JOptionPane.showConfirmDialog(this.cardPanel, "Remove your Claude key from this computer and switch the Prompter off?", "Remove key", JOptionPane.OK_CANCEL_OPTION);
    if (answer != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      KeyStores.get().delete();
    } catch (KeyStore.KeyStoreException e) {
      JOptionPane.showMessageDialog(this.cardPanel, e.getMessage(), "Remove key", JOptionPane.WARNING_MESSAGE);
    }
    this.session.close();
    PrompterEnabledState.getInstance().setValueTransactionlessly(false);
    this.refresh();
  }
}
