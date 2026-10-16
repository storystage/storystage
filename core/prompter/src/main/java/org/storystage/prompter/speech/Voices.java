/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import edu.cmu.cs.dennisc.java.util.logging.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Speaks the Prompter's step narrations with the computer's own voices: Windows speech, macOS
 * {@code say}, Linux speech-dispatcher or espeak-ng, Flite when on the PATH, else captions only.
 * See docs/spec/03-speech.md §2.
 */
public final class Voices {
  /** A text-to-speech provider. */
  public interface Voice {
    String name();

    /** Speaks after anything already queued; returns at once. */
    void speak(String text);

    /** Stops current and queued speech. */
    void stop();
  }

  static final int MAX_LENGTH = 300;
  private static Voice instance;

  private Voices() {
    throw new AssertionError();
  }

  /** The best voice available on this computer, chosen once. */
  public static synchronized Voice get() {
    if (instance == null) {
      instance = choose(System.getProperty("os.name", ""));
      Logger.outln("StoryStage speech:", instance.name());
    }
    return instance;
  }

  static Voice choose(String osName) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      return new WindowsVoice();
    }
    if (os.startsWith("mac") && onPath("say")) {
      return new CommandVoice("macOS voice", "say");
    }
    if (onPath("spd-say")) {
      return new CommandVoice("speech-dispatcher", "spd-say", "-w");
    }
    if (onPath("espeak-ng")) {
      return new CommandVoice("espeak-ng", "espeak-ng");
    }
    if (onPath("flite")) {
      return new CommandVoice("Flite", "flite", "-t");
    }
    return new CaptionsOnly();
  }

  /** Plain, single-line text of a sensible length, safe to pass to any provider. */
  public static String sanitise(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (char c : text.toCharArray()) {
      if (Character.isISOControl(c)) {
        sb.append(' ');
      } else if (c != '"' && c != '`' && c != '$') {
        sb.append(c);
      }
    }
    String s = sb.toString().replaceAll("\\s+", " ").trim();
    return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
  }

  static boolean onPath(String program) {
    String path = System.getenv("PATH");
    if (path == null) {
      return false;
    }
    for (String dir : path.split(File.pathSeparator)) {
      if (new File(dir, program).canExecute()) {
        return true;
      }
    }
    return false;
  }

  /** Windows: one long-lived PowerShell process using System.Speech, reading lines to speak. */
  static final class WindowsVoice implements Voice {
    private static final String STOP = "__STOP__";
    private static final String SCRIPT = String.join("\n",
        "Add-Type -AssemblyName System.Speech",
        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
        "$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'en-GB' } | Select-Object -First 1",
        "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }",
        "while ($true) {",
        "  $line = [Console]::In.ReadLine()",
        "  if ($line -eq $null) { break }",
        "  if ($line -eq '" + STOP + "') { $s.SpeakAsyncCancelAll(); continue }",
        "  [void]$s.SpeakAsync($line)",
        "}");
    private Process process;
    private BufferedWriter writer;

    @Override
    public String name() {
      return "Windows speech";
    }

    private synchronized boolean ensureStarted() {
      if (this.process != null && this.process.isAlive()) {
        return true;
      }
      try {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        this.process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        this.writer = new BufferedWriter(new OutputStreamWriter(this.process.getOutputStream(), StandardCharsets.UTF_8));
        return true;
      } catch (IOException e) {
        Logger.throwable(e, "Windows speech unavailable");
        return false;
      }
    }

    private synchronized void send(String line) {
      if (!this.ensureStarted()) {
        return;
      }
      try {
        this.writer.write(line);
        this.writer.newLine();
        this.writer.flush();
      } catch (IOException e) {
        this.process = null;
      }
    }

    @Override
    public void speak(String text) {
      String s = sanitise(text);
      if (!s.isEmpty()) {
        this.send(s);
      }
    }

    @Override
    public void stop() {
      if (this.process != null) {
        this.send(STOP);
      }
    }
  }

  /** macOS and Linux: one command per utterance, run in order on a background thread. */
  static final class CommandVoice implements Voice {
    private final String name;
    private final String[] command;
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "StoryStage speech");
      t.setDaemon(true);
      return t;
    });
    private volatile Process current;
    private volatile long generation;

    CommandVoice(String name, String... command) {
      this.name = name;
      this.command = command;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public void speak(String text) {
      String s = sanitise(text);
      if (s.isEmpty()) {
        return;
      }
      long myGeneration = this.generation;
      Future<?> unused = this.queue.submit(() -> {
        if (myGeneration != this.generation) {
          return;
        }
        String[] args = new String[this.command.length + 1];
        System.arraycopy(this.command, 0, args, 0, this.command.length);
        args[this.command.length] = s;
        try {
          this.current = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
          this.current.waitFor();
        } catch (IOException e) {
          Logger.throwable(e, "speech failed");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }

    @Override
    public void stop() {
      this.generation++;
      Process p = this.current;
      if (p != null) {
        p.destroy();
      }
    }
  }

  /** No voice available: the panel's captions are the only output. */
  static final class CaptionsOnly implements Voice {
    @Override
    public String name() {
      return "captions only";
    }

    @Override
    public void speak(String text) {
    }

    @Override
    public void stop() {
    }
  }
}
