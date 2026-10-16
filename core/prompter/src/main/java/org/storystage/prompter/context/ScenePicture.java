/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import edu.cmu.cs.dennisc.java.util.logging.Logger;
import org.alice.stageide.sceneeditor.ThumbnailGenerator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A small JPEG of the scene from the camera, rendered off screen the way Alice makes project
 * thumbnails. Sent with a request only when the learner allows it. See
 * docs/spec/01-prompter-protocol.md §2.4.
 */
public final class ScenePicture {
  static final int WIDTH = 640;
  static final int HEIGHT = 360;
  private static ThumbnailGenerator generator;

  private ScenePicture() {
  }

  /** The scene as JPEG bytes, or null if it can't be captured. Call on the Swing thread. */
  public static byte[] capture() {
    if (!ThumbnailGenerator.isOffscreenRenderingAllowed()) {
      return null;
    }
    try {
      if (generator == null) {
        generator = new ThumbnailGenerator(WIDTH, HEIGHT);
      }
      BufferedImage image = generator.createThumbnail();
      return image != null ? jpeg(image) : null;
    } catch (Throwable t) {
      Logger.throwable(t, "StoryStage couldn't capture the scene picture");
      return null;
    }
  }

  static byte[] jpeg(BufferedImage image) throws IOException {
    BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    rgb.getGraphics().drawImage(image, 0, 0, null);
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(0.8f);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      writer.write(null, new IIOImage(rgb, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
