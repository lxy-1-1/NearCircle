package com.nearcircle;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageOutputTest {
  @Test
  void detailedJpegCannotExpandBeyondTheMobileDownloadBudget() throws Exception {
    BufferedImage source = new BufferedImage(1600, 1600, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(42);
    for (int y = 0; y < 1600; y++)
      for (int x = 0; x < 1600; x++) source.setRGB(x, y, random.nextInt(0x1000000));
    var upload = new ByteArrayOutputStream();
    ImageIO.write(source, "jpeg", upload);
    assertThat(upload.size()).isLessThan(5 * 1024 * 1024);
    byte[] sanitized =
        Images.sanitize(
            new MockMultipartFile("file", "photo.jpg", "image/jpeg", upload.toByteArray()));
    assertThat(sanitized.length).isLessThanOrEqualTo(5 * 1024 * 1024);
    assertThat(ImageIO.read(new ByteArrayInputStream(sanitized))).isNotNull();
  }
}
