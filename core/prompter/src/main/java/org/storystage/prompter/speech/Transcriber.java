/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

import java.io.IOException;
import java.util.Locale;

/**
 * Turns recorded speech into text on this computer with whisper.cpp. Audio is never sent
 * anywhere. See docs/spec/03-speech.md §1.
 */
public final class Transcriber implements AutoCloseable {
  private static boolean libraryLoaded;
  private final WhisperJNI whisper = new WhisperJNI();
  private final WhisperContext context;

  /** Loads the model. Takes a few seconds; call off the Swing thread. */
  public Transcriber(WhisperModel model) throws IOException {
    synchronized (Transcriber.class) {
      if (!libraryLoaded) {
        WhisperJNI.loadLibrary();
        libraryLoaded = true;
      }
    }
    this.context = this.whisper.init(model.getPath());
  }

  /** Transcribes 16 kHz mono samples in the range -1 to 1. Blocking. */
  public synchronized String transcribe(float[] samples, Locale locale) throws IOException {
    WhisperFullParams params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
    params.language = locale != null && !locale.getLanguage().isEmpty() ? locale.getLanguage() : "en";
    params.translate = false;
    params.noTimestamps = true;
    params.printProgress = false;
    params.printRealtime = false;
    params.printTimestamps = false;
    params.suppressBlank = true;
    params.suppressNonSpeechTokens = true;
    params.nThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    int status = this.whisper.full(this.context, params, samples, samples.length);
    if (status != 0) {
      throw new IOException("Speech recognition failed (" + status + ").");
    }
    StringBuilder sb = new StringBuilder();
    int segments = this.whisper.fullNSegments(this.context);
    for (int i = 0; i < segments; i++) {
      sb.append(this.whisper.fullGetSegmentText(this.context, i));
    }
    return sb.toString().trim();
  }

  @Override
  public void close() {
    this.context.close();
  }
}
