package io.github.lxy.nearcircle.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.data.Session;

final class MediaLoader {
  private static final LruCache<String, Bitmap> CACHE =
      new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap image) {
          return image.getAllocationByteCount();
        }
      };

  static void load(NearApp app, Session session, String id, ImageView view) {
    String key = session.owner() + ":" + id;
    view.setTag(key);
    Bitmap cached = CACHE.get(key);
    if (cached != null) {
      view.setImageBitmap(cached);
      return;
    }
    app.io.execute(
        () -> {
          try {
            byte[] data = app.api(session).image(id);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1200
                || options.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (image == null) return;
            CACHE.put(key, image);
            view.post(
                () -> {
                  if (key.equals(view.getTag()) && app.sessions.matches(session))
                    view.setImageBitmap(image);
                });
          } catch (Exception ignored) {
            view.post(
                () -> {
                  if (key.equals(view.getTag())) view.setContentDescription("图片加载失败，可刷新重试");
                });
          }
        });
  }
}
