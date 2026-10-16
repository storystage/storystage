/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.LongConsumer;

/**
 * The whisper.cpp speech models StoryStage can use, downloaded once on first use and checked
 * against a pinned SHA-256. See docs/spec/03-speech.md §1.
 */
public enum WhisperModel {
  TINY("ggml-tiny.bin", 77_691_713L, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
  BASE("ggml-base.bin", 147_951_465L, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe");

  private static final String BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";
  private final String fileName;
  private final long size;
  private final String sha256;

  WhisperModel(String fileName, long size, String sha256) {
    this.fileName = fileName;
    this.size = size;
    this.sha256 = sha256;
  }

  public long getSize() {
    return this.size;
  }

  /** Where the model is kept on this computer. */
  public Path getPath() {
    return modelsDirectory().resolve(this.fileName);
  }

  public boolean isInstalled() {
    try {
      return Files.size(this.getPath()) == this.size;
    } catch (IOException e) {
      return false;
    }
  }

  /** Downloads and verifies the model, reporting bytes received. Blocking; call off the Swing thread. */
  public void download(LongConsumer progress) throws IOException {
    this.download(URI.create(BASE_URL + this.fileName), progress);
  }

  /** Downloads from {@code source}; a cancelled, failed or wrong download leaves no file behind. */
  void download(URI source, LongConsumer progress) throws IOException {
    Path target = this.getPath();
    Files.createDirectories(target.getParent());
    Path partial = target.resolveSibling(this.fileName + ".part");
    HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
    HttpRequest request = HttpRequest.newBuilder(source).GET().build();
    MessageDigest digest = sha256Digest();
    boolean verified = false;
    try {
      HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new IOException("The speech model download failed (HTTP " + response.statusCode() + ").");
      }
      try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partial)) {
        byte[] buffer = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) > 0) {
          out.write(buffer, 0, n);
          digest.update(buffer, 0, n);
          total += n;
          progress.accept(total);
        }
      }
      if (!HexFormat.of().formatHex(digest.digest()).equals(this.sha256)) {
        throw new IOException("The speech model didn't download correctly. Try again.");
      }
      Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      verified = true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("The speech model download was interrupted.", e);
    } finally {
      if (!verified) {
        Files.deleteIfExists(partial);
      }
    }
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The models folder for this operating system (spec 00 §6). */
  static Path modelsDirectory() {
    String override = System.getProperty("storystage.prompter.modelsDir");
    if (override != null) {
      return Paths.get(override);
    }
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String home = System.getProperty("user.home");
    if (os.startsWith("windows")) {
      String local = System.getenv("LOCALAPPDATA");
      return Paths.get(local != null ? local : home, "StoryStage", "models");
    } else if (os.startsWith("mac")) {
      return Paths.get(home, "Library", "Application Support", "StoryStage", "models");
    } else {
      return Paths.get(home, ".local", "share", "StoryStage", "models");
    }
  }
}
