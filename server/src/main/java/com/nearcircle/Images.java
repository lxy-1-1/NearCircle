package com.nearcircle;

import java.io.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

final class Images {
  private Images() {}

  static byte[] sanitize(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
      throw new ApiError(400, "图片需为 1 字节至 5 MiB");
    try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new ApiError(400, "不是有效的 PNG/JPEG/WebP 图片");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("png")
            && !format.equals("jpeg")
            && !format.equals("jpg")
            && !format.equals("webp")) throw new ApiError(400, "只支持 PNG/JPEG/WebP 图片");
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h > 16_000_000L)
          throw new ApiError(400, "图片最长边不能超过 4096，总像素不能超过 1600 万");
        var decoded = reader.read(0);
        while (true) {
          var out = new ByteArrayOutputStream();
          if (!ImageIO.write(decoded, "png", out)) throw new ApiError(400, "图片无法转换");
          if (out.size() <= 5 * 1024 * 1024) return out.toByteArray();
          // Detailed JPEGs may expand when stripped/re-encoded as PNG.
          // Keep stored media inside the same 5 MiB transport budget.
          var smaller =
              new java.awt.image.BufferedImage(
                  Math.max(1, decoded.getWidth() / 2),
                  Math.max(1, decoded.getHeight() / 2),
                  java.awt.image.BufferedImage.TYPE_INT_ARGB);
          var g = smaller.createGraphics();
          try {
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(decoded, 0, 0, smaller.getWidth(), smaller.getHeight(), null);
          } finally {
            g.dispose();
          }
          decoded = smaller;
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new ApiError(400, "图片损坏或格式无效");
    }
  }
}
