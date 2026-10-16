/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.storystage.prompter.claude.KeyCheck;
import org.storystage.prompter.key.ApiKeys;
import org.storystage.prompter.key.KeyStore;
import org.storystage.prompter.key.KeyStores;
import org.storystage.prompter.settings.SpokenStepsState;
import org.storystage.prompter.settings.VoiceInputState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * The Prompter set-up wizard: what it does, creating a Claude account, entering and checking the
 * key, and options. Completing it is the only way to switch the Prompter on. See
 * docs/spec/02-keys-privacy-switches.md §1.
 */
public final class SetupWizard extends JDialog {
  public static final String CONSOLE_ADDRESS = "https://platform.claude.com";
  private static final String[] PAGES = {"about", "account", "key", "options"};

  private final CardLayout cards = new CardLayout();
  private final JPanel pagePanel = new JPanel(this.cards);
  private final JButton backButton = new JButton("Back");
  private final JButton nextButton = new JButton("Next");
  private final JButton cancelButton = new JButton("Cancel");
  private final JPasswordField keyField = new JPasswordField(36);
  private final JCheckBox showKey = new JCheckBox("Show key");
  private final JButton checkButton = new JButton("Check key");
  private final JLabel checkResult = new JLabel(" ");
  private final JCheckBox voiceInput = new JCheckBox("Let me talk to the Prompter (voice input)");
  private final JCheckBox spokenSteps = new JCheckBox("Read the Prompter's steps aloud");
  private int page = 0;
  private boolean keyChecked = false;
  private boolean completed = false;

  private SetupWizard(Window owner) {
    super(owner, "Set up the StoryStage Prompter", ModalityType.APPLICATION_MODAL);
    this.pagePanel.add(this.createAboutPage(), PAGES[0]);
    this.pagePanel.add(this.createAccountPage(), PAGES[1]);
    this.pagePanel.add(this.createKeyPage(), PAGES[2]);
    this.pagePanel.add(this.createOptionsPage(), PAGES[3]);
    this.pagePanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(this.cancelButton);
    buttons.add(this.backButton);
    buttons.add(this.nextButton);
    this.backButton.addActionListener(e -> this.showPage(this.page - 1));
    this.nextButton.addActionListener(e -> this.next());
    this.cancelButton.addActionListener(e -> this.close());

    this.getContentPane().setLayout(new BorderLayout());
    this.getContentPane().add(this.pagePanel, BorderLayout.CENTER);
    this.getContentPane().add(buttons, BorderLayout.PAGE_END);
    this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    this.setPreferredSize(new Dimension(560, 440));
    this.pack();
    this.setLocationRelativeTo(owner);
    this.showPage(0);
  }

  /** Shows the wizard and returns true if it was completed and the key stored. */
  public static boolean showWizard(Component owner) {
    Window window = owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
    SetupWizard wizard = new SetupWizard(window);
    wizard.setVisible(true);
    return wizard.completed;
  }

  private static JEditorPane text(String html) {
    JEditorPane pane = new JEditorPane("text/html", "<html><body style='font-family:sans-serif'>" + html + "</body></html>");
    pane.setEditable(false);
    pane.setOpaque(false);
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    return pane;
  }

  private JComponent createAboutPage() {
    return text("<h2>What the Prompter does</h2>"
        + "<p>The Prompter builds scenes and code from what you type or say, using ordinary StoryStage blocks you can change or undo.</p>"
        + "<p><b>What is sent to Anthropic:</b> your request, your world's code and, if you allow it, a picture of the scene. "
        + "It goes from this computer to Anthropic under your own Claude account. StoryStage itself runs no servers.</p>"
        + "<p><b>What is never sent:</b> recordings of your voice (speech is turned into text on this computer), your files, "
        + "or your key to anyone but Anthropic.</p>"
        + "<p><b>Cost:</b> a typical request costs about 7 cents (US) on your Claude account.</p>"
        + "<p><b>The Claude account must belong to an adult (18 or over):</b> a parent, teacher or school.</p>");
  }

  private JComponent createAccountPage() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(text("<h2>Create your Claude account</h2>"
        + "<ol>"
        + "<li>Go to the Claude Console (address below) and sign up.</li>"
        + "<li>Add credit or billing.</li>"
        + "<li>Create a workspace called <b>StoryStage</b> and set a monthly <b>spend limit</b>, so the Prompter can never cost more than you choose.</li>"
        + "<li>In that workspace, create an <b>API key</b> and copy it.</li>"
        + "</ol>"), BorderLayout.CENTER);
    JTextField address = new JTextField(CONSOLE_ADDRESS);
    address.setEditable(false);
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.add(new JLabel("Console address (copy it into your browser):"), BorderLayout.LINE_START);
    row.add(address, BorderLayout.CENTER);
    panel.add(row, BorderLayout.PAGE_END);
    return panel;
  }

  private JComponent createKeyPage() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    JEditorPane intro = text("<h2>Enter your key</h2><p>Paste the API key you created. "
        + "StoryStage keeps it in this computer's secure credential store (" + KeyStores.get().describe() + ") and sends it only to Anthropic.</p>");
    intro.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(intro);
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    row.add(this.keyField);
    row.add(Box.createHorizontalStrut(8));
    row.add(this.showKey);
    row.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(row);
    JPanel checkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
    checkRow.add(this.checkButton);
    checkRow.add(Box.createHorizontalStrut(12));
    checkRow.add(this.checkResult);
    checkRow.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(checkRow);

    char echo = this.keyField.getEchoChar();
    this.showKey.addActionListener(e -> this.keyField.setEchoChar(this.showKey.isSelected() ? (char) 0 : echo));
    this.checkButton.addActionListener(e -> this.checkKey());
    this.keyField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        keyEdited();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        keyEdited();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        keyEdited();
      }
    });
    return panel;
  }

  private JComponent createOptionsPage() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    JEditorPane intro = text("<h2>Options</h2><p>You can change these later in Preferences.</p>");
    intro.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(intro);
    this.voiceInput.setSelected(VoiceInputState.getInstance().getValue());
    this.voiceInput.setEnabled(VoiceInputState.getInstance().isEnabled());
    this.spokenSteps.setSelected(SpokenStepsState.getInstance().getValue());
    this.voiceInput.setAlignmentX(LEFT_ALIGNMENT);
    this.spokenSteps.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(this.voiceInput);
    panel.add(this.spokenSteps);
    return panel;
  }

  private void keyEdited() {
    this.keyChecked = false;
    this.checkResult.setText(" ");
    this.updateButtons();
  }

  private void checkKey() {
    String key = this.currentKey();
    this.checkButton.setEnabled(false);
    this.checkResult.setText("Checking...");
    new SwingWorker<KeyCheck.Result, Void>() {
      @Override
      protected KeyCheck.Result doInBackground() {
        return KeyCheck.check(key);
      }

      @Override
      protected void done() {
        KeyCheck.Result result;
        try {
          result = this.get();
        } catch (InterruptedException | ExecutionException e) {
          result = KeyCheck.Result.FAILED;
        }
        SetupWizard.this.keyChecked = result == KeyCheck.Result.OK;
        SetupWizard.this.checkResult.setText(result.getMessage());
        SetupWizard.this.checkButton.setEnabled(true);
        SetupWizard.this.updateButtons();
      }
    }.execute();
  }

  private String currentKey() {
    char[] chars = this.keyField.getPassword();
    String key = ApiKeys.clean(new String(chars));
    Arrays.fill(chars, ' ');
    return key;
  }

  private void next() {
    if (this.page < PAGES.length - 1) {
      this.showPage(this.page + 1);
    } else {
      this.finish();
    }
  }

  private void finish() {
    KeyStore store = KeyStores.get();
    try {
      store.write(this.currentKey());
    } catch (KeyStore.KeyStoreException e) {
      this.checkResult.setText(e.getMessage());
      this.showPage(2);
      return;
    }
    VoiceInputState.getInstance().setValueTransactionlessly(this.voiceInput.isSelected() && this.voiceInput.isEnabled());
    SpokenStepsState.getInstance().setValueTransactionlessly(this.spokenSteps.isSelected());
    this.completed = true;
    this.close();
  }

  private void close() {
    this.keyField.setText("");
    this.setVisible(false);
    this.dispose();
  }

  private void showPage(int index) {
    this.page = Math.max(0, Math.min(PAGES.length - 1, index));
    this.cards.show(this.pagePanel, PAGES[this.page]);
    this.updateButtons();
  }

  private void updateButtons() {
    this.backButton.setEnabled(this.page > 0);
    this.nextButton.setText(this.page == PAGES.length - 1 ? "Finish" : "Next");
    this.nextButton.setEnabled(this.page != 2 || this.keyChecked);
    this.getRootPane().setDefaultButton(this.nextButton);
  }
}
