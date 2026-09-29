package io.github.lxy.nearcircle.ui;

import static org.junit.Assert.*;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.data.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FeedRefreshRegressionTest {
  @Test
  public void refreshDuringFeedLoadFetchesNewSnapshot() throws Exception {
    NearApp app = ApplicationProvider.getApplicationContext();
    Session previous = app.sessions.get();
    String previousServer = app.sessions.lastServer();
    CountDownLatch firstRequest = new CountDownLatch(1),
        releaseFirst = new CountDownLatch(1),
        secondRequest = new CountDownLatch(1);
    AtomicInteger requests = new AtomicInteger();
    try (MockWebServer server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
              if (request.getPath().startsWith("/api/posts")) {
                if (requests.incrementAndGet() == 1) {
                  firstRequest.countDown();
                  releaseFirst.await(10, TimeUnit.SECONDS);
                  return json("{\"items\":[],\"next_cursor\":null}");
                }
                secondRequest.countDown();
                return json(
                    "{\"items\":[{\"id\":10,\"author\":{\"id\":992,\"username\":\"friend\",\"display_name\":\"Friend\",\"bio\":\"\"},\"body\":\"Arrived"
                        + " during"
                        + " refresh\",\"media_id\":null,\"created_at\":1000,\"liked\":false,\"like_count\":0,\"comments\":[]}],\"next_cursor\":null}");
              }
              if (request.getPath().equals("/api/ws-ticket"))
                return new MockResponse().setResponseCode(503).setBody("{}");
              return json("{\"items\":[]}");
            }
          });
      server.start();
      JsonObject user = new JsonObject();
      user.addProperty("id", 991);
      user.addProperty("username", "regression");
      app.sessions.save(server.url("/").toString().replaceAll("/+$", ""), "feed-token", user);
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        scenario.onActivity(
            activity -> {
              View tab = findDescription(activity.getWindow().getDecorView(), "朋友圈");
              assertNotNull(tab);
              tab.performClick();
            });
        assertTrue(firstRequest.await(10, TimeUnit.SECONDS));
        scenario.onActivity(
            activity -> {
              activity.refresh();
              activity.refresh();
            });
        releaseFirst.countDown();
        assertTrue(
            "A refresh received in flight must cause another HTTP snapshot",
            secondRequest.await(10, TimeUnit.SECONDS));
        AtomicBoolean visible = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!visible.get() && SystemClock.uptimeMillis() < deadline) {
          scenario.onActivity(
              activity ->
                  visible.set(
                      hasText(activity.getWindow().getDecorView(), "Arrived during refresh")));
          if (!visible.get()) SystemClock.sleep(25);
        }
        assertTrue("The new post should be rendered", visible.get());
        assertEquals("Events during a load should coalesce", 2, requests.get());
      }
    } finally {
      releaseFirst.countDown();
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

  private static MockResponse json(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private static View findDescription(View view, String description) {
    if (description.contentEquals(
        view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View result = findDescription(group.getChildAt(i), description);
        if (result != null) return result;
      }
    }
    return null;
  }

  private static boolean hasText(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (hasText(group.getChildAt(i), text)) return true;
    }
    return false;
  }
}
