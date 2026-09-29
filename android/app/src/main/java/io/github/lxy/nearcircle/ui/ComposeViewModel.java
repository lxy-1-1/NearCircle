package io.github.lxy.nearcircle.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.data.Session;
import io.github.lxy.nearcircle.net.ApiClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ComposeViewModel extends AndroidViewModel {
  public final MutableLiveData<String> state = new MutableLiveData<>("");
  public final MutableLiveData<Bitmap> preview = new MutableLiveData<>();
  public Uri image;
  private byte[] encoded;
  private final Handler main = new Handler(Looper.getMainLooper());
  private long imageGeneration;

  public ComposeViewModel(@NonNull Application app) {
    super(app);
  }

  public void image(Uri uri) {
    long generation = ++imageGeneration;
    image = uri;
    encoded = null;
    if (uri == null) {
      preview.setValue(null);
      state.setValue("");
      return;
    }
    state.setValue("PHOTO");
    NearApp app = getApplication();
    app.io.execute(
        () -> {
          try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
              BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0)
              throw new IllegalArgumentException("无法读取这张图片");
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1600
                || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            Bitmap bitmap;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
              bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) throw new IllegalArgumentException("无法读取这张图片");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes);
            byte[] photo = bytes.toByteArray();
            main.post(
                () -> {
                  if (generation != imageGeneration) return;
                  encoded = photo;
                  preview.setValue(bitmap);
                  state.setValue("");
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (generation != imageGeneration) return;
                  image = null;
                  preview.setValue(null);
                  state.setValue("图片读取失败，请重新选择");
                });
          }
        });
  }

  @Override
  protected void onCleared() {
    imageGeneration++;
    super.onCleared();
  }

  public void publish(Session session, String text) {
    if ("BUSY".equals(state.getValue()) || "PHOTO".equals(state.getValue())) return;
    String body = text.trim();
    if (body.isEmpty() && encoded == null) {
      state.setValue("写点什么，或选择一张图片");
      return;
    }
    state.setValue("BUSY");
    NearApp app = getApplication();
    byte[] photo = encoded;
    app.io.execute(
        () -> {
          boolean submitted = false;
          try {
            if (!app.sessions.matches(session))
              throw new IllegalArgumentException("登录状态已改变，请返回后重新发布");
            JsonObject payload = new JsonObject();
            payload.addProperty("body", body);
            if (photo != null)
              payload.addProperty(
                  "media_id", app.api(session).upload(photo).get("id").getAsString());
            if (!app.sessions.matches(session)) throw new IllegalArgumentException("登录状态已改变");
            submitted = true;
            app.api(session).post("/posts", payload);
            state.postValue("DONE");
          } catch (Exception e) {
            state.postValue(
                e instanceof ApiClient.ApiException || e instanceof IllegalArgumentException
                    ? e.getMessage()
                    : submitted ? "发布结果未确认，请返回朋友圈核对后再操作" : "无法上传图片，请检查网络后重试");
          }
        });
  }
}
