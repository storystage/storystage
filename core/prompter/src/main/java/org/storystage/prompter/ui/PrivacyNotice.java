/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.storystage.StoryStage;

import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Dimension;

/**
 * What the Prompter sends and keeps, shown on the wizard's first page and from the panel's
 * "What is sent?" button. Matches docs/privacy.md. See docs/spec/02-keys-privacy-switches.md §5.
 */
final class PrivacyNotice {
  static final String ANTHROPIC_TERMS = "https://www.anthropic.com/legal/commercial-terms";
  static final String ANTHROPIC_PRIVACY = "https://www.anthropic.com/legal/privacy";

  private PrivacyNotice() {
  }

  /** The notice as HTML body content. */
  static String html() {
    return "<p><b>What is sent to Anthropic:</b> your typed or spoken request (as text) and your world's code. "
        + "It goes straight from this computer to Anthropic under your own Claude account and its terms "
        + "(" + ANTHROPIC_TERMS + ", " + ANTHROPIC_PRIVACY + "). StoryStage itself runs no servers and receives none of your data.</p>"
        + "<p><b>What is never sent:</b> recordings of your voice (speech is turned into text on this computer), "
        + "your other files, or your key to anyone but Anthropic.</p>"
        + "<p><b>Your key</b> is kept in this computer's secure credential store. Remove it at any time with <b>Remove key</b>.</p>"
        + "<p><b>Cost:</b> a typical request costs about 7 US cents on your Claude account. Set a spend limit in the Claude Console.</p>"
        + "<p><b>Schools</b> can turn the Prompter off, or set it to explain only, for everyone on a computer.</p>";
  }

  static void show(Component parent) {
    JEditorPane pane = new JEditorPane("text/html", "<html><body style='font-family:sans-serif'><h3>What the Prompter sends</h3>" + html()
        + "<p>More at " + StoryStage.WEBSITE + "</p></body></html>");
    pane.setEditable(false);
    pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    pane.setCaretPosition(0);
    JScrollPane scroll = new JScrollPane(pane);
    scroll.setPreferredSize(new Dimension(460, 320));
    JOptionPane.showMessageDialog(parent, scroll, "What is sent?", JOptionPane.PLAIN_MESSAGE);
  }
}
