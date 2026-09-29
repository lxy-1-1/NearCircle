package io.github.lxy.nearcircle.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.data.Session;
import io.github.lxy.nearcircle.net.ApiClient;

public abstract class BaseActivity extends AppCompatActivity {
  protected NearApp app;
  protected Session session;

  @Override
  protected void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    app = (NearApp) getApplication();
    session = app.sessions.get();
  }

  protected boolean requireSession() {
    if (session == null) {
      login();
      return false;
    }
    return true;
  }

  protected void root(View root) {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(true);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          return insets;
        });
    root.setBackgroundColor(Ui.BG);
    setContentView(root);
    ViewCompat.requestApplyInsets(root);
  }

  protected void login() {
    startActivity(
        new Intent(this, LoginActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    finish();
  }

  protected void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  public interface Request {
    JsonObject execute() throws Exception;
  }

  public interface Success {
    void accept(JsonObject result);
  }

  protected void request(Request request, Success success) {
    Session bound = session;
    app.io.execute(
        () -> {
          try {
            JsonObject result = request.execute();
            runOnUiThread(
                () -> {
                  if (!isDestroyed() && app.sessions.matches(bound)) success.accept(result);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed() || !app.sessions.matches(bound)) return;
                  if (e instanceof ApiClient.ApiException
                      && ((ApiClient.ApiException) e).code == 401) {
                    if (app.logoutIfCurrent(bound)) {
                      toast("登录已失效，请重新登录");
                      login();
                    }
                  } else onRequestError(e);
                });
          }
        });
  }

  protected void onRequestError(Exception e) {
    toast(e instanceof ApiClient.ApiException ? e.getMessage() : "暂时无法连接服务，请检查网络后重试");
  }

  protected static String str(JsonObject object, String key) {
    return Session.text(object, key);
  }

  protected static long id(JsonObject object) {
    return object.get("id").getAsLong();
  }
}
