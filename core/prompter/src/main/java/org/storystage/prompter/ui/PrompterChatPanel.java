/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import com.fasterxml.jackson.databind.JsonNode;
import org.storystage.prompter.claude.Json;
import org.storystage.prompter.claude.PrompterConversation;
import org.storystage.prompter.claude.ToolExecutor;
import org.storystage.prompter.settings.PrompterPolicy;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * The Prompter conversation: transcript with each step's caption, a mode choice, the request box
 * and Send, Stop and Undo this request. See docs/spec/04-ux.md §2–3.
 */
final class PrompterChatPanel extends JPanel implements PrompterSession.View {
  private static final String[] MODE_NAMES = {"Show me how", "Build it", "Explain only"};
  private static final PrompterConversation.Mode[] MODES = {
    PrompterConversation.Mode.SHOW_ME_HOW, PrompterConversation.Mode.BUILD_IT, PrompterConversation.Mode.EXPLAIN_ONLY,
  };

  private final PrompterSession session;
  private final JTextArea transcript = new JTextArea();
  private final JTextArea input = new JTextArea(3, 20);
  private final JComboBox<String> mode = new JComboBox<>(MODE_NAMES);
  private final JButton send = new JButton("Send");
  private final JButton stop = new JButton("Stop");
  private final JButton undo = new JButton("Undo this request");
  private final JButton newConversation = new JButton("New conversation");
  private final JLabel status = new JLabel(" ");
  private final JLabel usage = new JLabel(" ");
  private Consumer<String> narrationListener = text -> { };
  private boolean busy;
  private boolean prompterSpeaking;

  PrompterChatPanel(PrompterSession session, PrompterPolicy policy) {
    super(new BorderLayout(0, 6));
    this.session = session;
    this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JPanel header = new JPanel(new BorderLayout(6, 0));
    header.add(new JLabel("Prompter"), BorderLayout.LINE_START);
    if (policy.isExplainOnly()) {
      this.mode.setSelectedIndex(2);
      this.mode.setEnabled(false);
      this.mode.setToolTipText("Your school has set the Prompter to explain only.");
    }
    header.add(this.mode, BorderLayout.LINE_END);
    this.add(header, BorderLayout.PAGE_START);

    this.transcript.setEditable(false);
    this.transcript.setLineWrap(true);
    this.transcript.setWrapStyleWord(true);
    this.transcript.setText("Prompter: Hi! Tell me what to put on stage, or ask me about your code.\n");
    this.add(new JScrollPane(this.transcript), BorderLayout.CENTER);

    this.input.setLineWrap(true);
    this.input.setWrapStyleWord(true);
    this.input.setToolTipText("Tell the Prompter what to stage. Ctrl+Enter sends.");
    this.input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "send");
    this.input.getActionMap().put("send", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PrompterChatPanel.this.send();
      }
    });

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(this.send);
    buttons.add(this.stop);
    buttons.add(this.undo);
    buttons.add(this.newConversation);
    JPanel south = new JPanel(new BorderLayout(0, 4));
    south.add(new JScrollPane(this.input), BorderLayout.PAGE_START);
    south.add(buttons, BorderLayout.CENTER);
    JPanel labels = new JPanel(new BorderLayout());
    labels.add(this.status, BorderLayout.PAGE_START);
    labels.add(this.usage, BorderLayout.PAGE_END);
    south.add(labels, BorderLayout.PAGE_END);
    this.add(south, BorderLayout.PAGE_END);

    this.send.addActionListener(e -> this.send());
    this.stop.addActionListener(e -> {
      this.session.stop();
      org.storystage.prompter.speech.Voices.get().stop();
      this.status.setText("Stopping...");
    });
    this.undo.addActionListener(e -> {
      int n = this.session.undoLastRequest();
      this.append(n > 0 ? "  (Undid " + n + " step" + (n == 1 ? "" : "s") + ".)\n" : "  (Nothing to undo: other changes were made since.)\n");
      this.updateButtons();
    });
    this.newConversation.addActionListener(e -> {
      this.session.newConversation();
      this.transcript.setText("Prompter: New conversation. What shall we stage?\n");
    });
    this.updateButtons();
  }

  /** Receives each step's narration, for spoken steps. */
  void setNarrationListener(Consumer<String> listener) {
    this.narrationListener = listener != null ? listener : text -> { };
  }

  private void send() {
    String request = this.input.getText().trim();
    if (request.isEmpty() || this.busy) {
      return;
    }
    PrompterConversation.Mode m = MODES[Math.max(0, this.mode.getSelectedIndex())];
    if (!this.session.send(request, m, this)) {
      this.status.setText("Open a world and set up the Prompter first.");
      return;
    }
    this.input.setText("");
    this.append("\nYou: " + request + "\n");
    this.prompterSpeaking = false;
    this.busy = true;
    this.status.setText("The Prompter is thinking...");
    this.updateButtons();
  }

  private void append(String text) {
    this.transcript.append(text);
    this.transcript.setCaretPosition(this.transcript.getDocument().getLength());
  }

  private void updateButtons() {
    this.send.setEnabled(!this.busy);
    this.stop.setEnabled(this.busy);
    this.undo.setEnabled(!this.busy && this.session.canUndoLastRequest());
    this.newConversation.setEnabled(!this.busy);
  }

  @Override
  public void prompterText(String delta) {
    if (!this.prompterSpeaking) {
      this.append("Prompter: ");
      this.prompterSpeaking = true;
    }
    this.append(delta);
  }

  @Override
  public void stepFinished(String toolName, ToolExecutor.Result result) {
    if (this.prompterSpeaking) {
      this.append("\n");
      this.prompterSpeaking = false;
    }
    if (result.isOk()) {
      String narration = result.getNarration();
      if (narration != null && !narration.isEmpty()) {
        this.append((toolName.equals("explain") ? "Prompter: " : "  - ") + narration + "\n");
        this.narrationListener.accept(narration);
      }
      this.status.setText("The Prompter is building...");
    } else {
      String message;
      try {
        JsonNode error = Json.MAPPER.readTree(result.getJson());
        message = error.path("message").asText("A step didn't work.");
      } catch (java.io.IOException e) {
        message = "A step didn't work.";
      }
      this.append("  (A step didn't work; the Prompter is trying again.)\n");
      this.status.setText(message);
    }
  }

  @Override
  public void requestFinished(PrompterConversation.Outcome outcome, String message) {
    if (this.prompterSpeaking) {
      this.append("\n");
      this.prompterSpeaking = false;
    }
    if (message != null) {
      this.append("Prompter: " + message + "\n");
    }
    this.busy = false;
    this.status.setText(outcome == PrompterConversation.Outcome.DONE ? "Done. Press Rehearse to see it." : " ");
    this.updateButtons();
  }

  @Override
  public void usageChanged(String summary) {
    this.usage.setText(summary + " (estimate)");
  }
}
