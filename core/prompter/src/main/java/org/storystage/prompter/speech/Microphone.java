/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Records from the microphone only while "Hold to talk" is held: 16 kHz, 16-bit, mono, at most
 * 30 seconds. The recording stays in memory. See docs/spec/03-speech.md §1.
 */
public final class Microphone {
  static final float SAMPLE_RATE = 16_000f;
  static final int MAX_SECONDS = 30;
  private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

  private TargetDataLine line;
  private Thread reader;
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  /** Starts recording. Throws if there is no microphone. */
  public synchronized void start() throws LineUnavailableException {
    if (this.line != null) {
      return;
    }
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
    if (!AudioSystem.isLineSupported(info)) {
      throw new LineUnavailableException("No microphone found.");
    }
    this.line = (TargetDataLine) AudioSystem.getLine(info);
    this.line.open(FORMAT);
    this.buffer.reset();
    this.line.start();
    TargetDataLine l = this.line;
    this.reader = new Thread(() -> {
      byte[] chunk = new byte[3200];
      int maxBytes = (int) (SAMPLE_RATE * 2 * MAX_SECONDS);
      while (l.isOpen()) {
        int n = l.read(chunk, 0, chunk.length);
        if (n <= 0) {
          break;
        }
        synchronized (this.buffer) {
          if (this.buffer.size() + n <= maxBytes) {
            this.buffer.write(chunk, 0, n);
          }
        }
      }
    }, "StoryStage microphone");
    this.reader.setDaemon(true);
    this.reader.start();
  }

  /** Stops recording and returns the samples as floats from -1 to 1. */
  public synchronized float[] stop() {
    if (this.line == null) {
      return new float[0];
    }
    this.line.stop();
    this.line.close();
    this.line = null;
    try {
      this.reader.join(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    byte[] bytes;
    synchronized (this.buffer) {
      bytes = this.buffer.toByteArray();
      this.buffer.reset();
    }
    return toFloats(bytes);
  }

  /** Little-endian signed 16-bit PCM to floats. */
  static float[] toFloats(byte[] pcm) {
    float[] rv = new float[pcm.length / 2];
    for (int i = 0; i < rv.length; i++) {
      int lo = pcm[2 * i] & 0xff;
      int hi = pcm[2 * i + 1];
      rv[i] = (short) ((hi << 8) | lo) / 32768f;
    }
    return rv;
  }
}
