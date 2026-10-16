/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScenePictureTest {
  @Test
  public void jpegIsSmallAndReadable() throws IOException {
    BufferedImage image = new BufferedImage(ScenePicture.WIDTH, ScenePicture.HEIGHT, BufferedImage.TYPE_INT_ARGB);
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        image.setRGB(x, y, 0xff000000 | (x << 8) | y);
      }
    }
    byte[] jpeg = ScenePicture.jpeg(image);
    assertTrue("under 200 KB", jpeg.length < 200_000);
    BufferedImage back = ImageIO.read(new ByteArrayInputStream(jpeg));
    assertEquals(ScenePicture.WIDTH, back.getWidth());
    assertEquals(ScenePicture.HEIGHT, back.getHeight());
  }
}
