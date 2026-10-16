/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import org.storystage.prompter.speech.Microphone;
import org.storystage.prompter.speech.Transcriber;
import org.storystage.prompter.speech.WhisperModel;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Hold to talk (button, or Ctrl+Space in the request box): records while held, transcribes on
 * this computer and puts the words in the request box for the learner to check before Send.
 * See docs/spec/03-speech.md §1.
 */
final class HoldToTalk {
  /** Shorter recordings are almost always an accidental click. */
  private static final int MIN_SAMPLES = 8_000;
  private static final String IDLE_TEXT = "Hold to talk";

  private final JButton button = new JButton(IDLE_TEXT);
  private final JTextArea input;
  private final Consumer<String> status;
  private final WhisperModel model;
  private final Microphone microphone = new Microphone();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "StoryStage speech");
    t.setDaemon(true);
    return t;
  });
  private Transcriber transcriber;
  private boolean recording;
  private boolean transcribing;
  private boolean busy;

  HoldToTalk(JTextArea input, Consumer<String> status) {
    this.input = input;
    this.status = status;
    this.model = "base".equalsIgnoreCase(System.getProperty("storystage.prompter.whisperModel")) ? WhisperModel.BASE : WhisperModel.TINY;
    this.button.setToolTipText("Hold to speak your request, then let go. You can also hold Ctrl+Space. Your voice stays on this computer.");
    this.button.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && HoldToTalk.this.button.isEnabled()) {
          HoldToTalk.this.press();
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          HoldToTalk.this.release();
        }
      }
    });
    input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl pressed SPACE"), "talkStart");
    input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl released SPACE"), "talkStop");
    input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released CONTROL"), "talkStop");
    input.getActionMap().put("talkStart", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (HoldToTalk.this.button.isVisible() && HoldToTalk.this.button.isEnabled()) {
          HoldToTalk.this.press();
        }
      }
    });
    input.getActionMap().put("talkStop", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        HoldToTalk.this.release();
      }
    });
  }

  JButton getButton() {
    return this.button;
  }

  /** Voice input waits while the Prompter is working on a request. */
  void setBusy(boolean busy) {
    this.busy = busy;
    this.button.setEnabled(!busy && !this.transcribing);
  }

  private void press() {
    if (this.recording || this.transcribing) {
      return;
    }
    if (!this.model.isInstalled()) {
      this.offerDownload();
      return;
    }
    try {
      this.microphone.start();
    } catch (Exception e) {
      this.status.accept("No microphone found.");
      return;
    }
    this.recording = true;
    this.button.setText("Listening... let go to stop");
    this.status.accept("Listening...");
    // Loading the model takes several seconds on a slow computer, so start while the learner speaks.
    this.worker.submit(() -> {
      try {
        this.loadTranscriber();
      } catch (IOException | LinkageError e) {
        // Tried again, and reported, when the recording is transcribed.
      }
    });
  }

  /** Runs on the speech thread only. */
  private void loadTranscriber() throws IOException {
    if (this.transcriber == null) {
      this.transcriber = new Transcriber(this.model);
    }
  }

  private void release() {
    if (!this.recording) {
      return;
    }
    this.recording = false;
    float[] samples = this.microphone.stop();
    this.button.setText(IDLE_TEXT);
    if (samples.length < MIN_SAMPLES) {
      this.status.accept("Hold the button while you speak.");
      return;
    }
    this.transcribing = true;
    this.button.setEnabled(false);
    this.status.accept("Working out what you said...");
    Locale locale = Locale.getDefault();
    this.worker.submit(() -> {
      String text;
      String problem = null;
      try {
        this.loadTranscriber();
        text = this.transcriber.transcribe(samples, locale);
      } catch (IOException e) {
        text = "";
        problem = e.getMessage();
      } catch (LinkageError e) {
        text = "";
        problem = "Voice input isn't available on this computer.";
      }
      String heard = text;
      String trouble = problem;
      SwingUtilities.invokeLater(() -> this.finished(heard, trouble));
    });
  }

  private void finished(String heard, String problem) {
    this.transcribing = false;
    this.button.setEnabled(!this.busy);
    if (problem != null) {
      this.status.accept(problem);
    } else if (heard.isEmpty()) {
      this.status.accept("I didn't catch that. Try again a little closer to the microphone.");
    } else {
      String existing = this.input.getText().trim();
      this.input.setText(existing.isEmpty() ? heard : existing + " " + heard);
      this.input.requestFocusInWindow();
      this.status.accept("Check the words, then press Send.");
    }
  }

  private void offerDownload() {
    long megabytes = Math.round(this.model.getSize() / 1_000_000.0);
    int answer = JOptionPane.showConfirmDialog(this.button,
        "<html>Voice input needs a speech model (about " + megabytes + " MB), downloaded once from Hugging Face."
            + "<br>After that, your voice is turned into text on this computer and never leaves it.<br><br>Download it now?</html>",
        "Voice input", JOptionPane.OK_CANCEL_OPTION);
    if (answer != JOptionPane.OK_OPTION) {
      this.status.accept("Voice input needs the speech model. You can type instead.");
      return;
    }
    ProgressMonitor monitor = new ProgressMonitor(this.button, "Downloading the speech model", null, 0, (int) (this.model.getSize() / 1024));
    monitor.setMillisToDecideToPopup(0);
    monitor.setMillisToPopup(0);
    this.button.setEnabled(false);
    this.status.accept("Downloading the speech model...");
    this.worker.submit(() -> {
      String problem = null;
      try {
        this.model.download(bytes -> {
          if (monitor.isCanceled()) {
            throw new CancellationException();
          }
          SwingUtilities.invokeLater(() -> monitor.setProgress((int) (bytes / 1024)));
        });
      } catch (CancellationException e) {
        problem = "Download cancelled. You can type instead.";
      } catch (IOException e) {
        problem = e.getMessage();
      }
      String trouble = problem;
      SwingUtilities.invokeLater(() -> {
        monitor.close();
        this.button.setEnabled(!this.busy);
        this.status.accept(trouble != null ? trouble : "Speech model ready. Hold the button and speak.");
      });
    });
  }
}
