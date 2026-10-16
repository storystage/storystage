/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.speech;

import com.sun.net.httpserver.HttpServer;
import io.github.givimad.whisperjni.WhisperJNI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SpeechInputTest {
  private Path folder;
  private HttpServer server;

  @Before
  public void setUp() throws IOException {
    this.folder = Files.createTempDirectory("storystage-models");
    System.setProperty("storystage.prompter.modelsDir", this.folder.toString());
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext("/model", exchange -> {
      byte[] body = "not really a speech model".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    this.server.createContext("/missing", exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });
    this.server.start();
  }

  @After
  public void tearDown() throws IOException {
    this.server.stop(0);
    System.clearProperty("storystage.prompter.modelsDir");
    try (Stream<Path> files = Files.walk(this.folder)) {
      files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
    }
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + path);
  }

  private long filesInFolder() throws IOException {
    try (Stream<Path> files = Files.list(this.folder)) {
      return files.count();
    }
  }

  @Test
  public void modelsFolderCanBeOverridden() {
    assertEquals(this.folder.resolve("ggml-tiny.bin"), WhisperModel.TINY.getPath());
    assertFalse(WhisperModel.TINY.isInstalled());
  }

  @Test
  public void wrongDownloadLeavesNothingBehind() throws IOException {
    try {
      WhisperModel.TINY.download(uri("/model"), bytes -> { });
      fail("a file with the wrong checksum must be refused");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("didn't download correctly"));
    }
    assertEquals(0, filesInFolder());
    assertFalse(WhisperModel.TINY.isInstalled());
  }

  @Test
  public void failedDownloadLeavesNothingBehind() throws IOException {
    try {
      WhisperModel.BASE.download(uri("/missing"), bytes -> { });
      fail("HTTP 404 must fail");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("404"));
    }
    assertEquals(0, filesInFolder());
  }

  @Test
  public void cancelledDownloadLeavesNothingBehind() throws IOException {
    try {
      WhisperModel.TINY.download(uri("/model"), bytes -> {
        throw new java.util.concurrent.CancellationException();
      });
      fail("cancel must stop the download");
    } catch (java.util.concurrent.CancellationException e) {
      // expected
    }
    assertEquals(0, filesInFolder());
  }

  @Test
  public void pcmBecomesFloats() {
    byte[] pcm = {0x00, 0x00, (byte) 0xff, 0x7f, 0x00, (byte) 0x80, (byte) 0xff, (byte) 0xff};
    float[] samples = Microphone.toFloats(pcm);
    assertEquals(4, samples.length);
    assertEquals(0f, samples[0], 0f);
    assertEquals(32767f / 32768f, samples[1], 1e-6f);
    assertEquals(-1f, samples[2], 0f);
    assertEquals(-1f / 32768f, samples[3], 1e-6f);
  }

  @Test
  public void nativeLibraryLoadsOnWindows() throws IOException {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      return;
    }
    // The packaged app relies on the win-amd64 library inside the whisper-jni jar.
    WhisperJNI.loadLibrary();
  }
}
