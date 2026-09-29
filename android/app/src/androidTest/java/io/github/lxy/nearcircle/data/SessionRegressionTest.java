package io.github.lxy.nearcircle.data;

import static org.junit.Assert.*;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SessionRegressionTest {
  @Test
  public void oldUnauthorizedResponseCannotClearNewLogin() throws Exception {
    NearApp app = ApplicationProvider.getApplicationContext();
    Session previous = app.sessions.get();
    String previousServer = app.sessions.lastServer();
    try {
      JsonObject user = new JsonObject();
      user.addProperty("id", 991);
      user.addProperty("username", "regression");
      app.sessions.save("http://127.0.0.1:1", "old-token", user);
      Session expired = app.sessions.get();
      app.sessions.save("http://127.0.0.1:1", "new-token", user);
      assertFalse(app.logoutIfCurrent(expired));
      assertEquals("new-token", app.sessions.get().token);
      assertTrue(app.sessions.matches(app.sessions.get()));
    } finally {
      app.sessions.clear();
      if (previous != null) {
        JsonObject user = new JsonObject();
        user.addProperty("id", previous.userId);
        user.addProperty("username", previous.username);
        user.addProperty("display_name", previous.displayName);
        user.addProperty("bio", previous.bio);
        app.sessions.save(previous.baseUrl, previous.token, user);
      }
      app.getSharedPreferences("session", android.content.Context.MODE_PRIVATE)
          .edit()
          .putString("server", previousServer)
          .commit();
    }
  }
}
