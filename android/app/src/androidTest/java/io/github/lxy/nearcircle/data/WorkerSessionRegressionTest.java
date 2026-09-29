package io.github.lxy.nearcircle.data;

import static org.junit.Assert.*;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WorkerSessionRegressionTest {
  @Test
  public void delayedUnauthorizedFromPreviousLoginKeepsOutboxQueued() throws Exception {
    NearApp app = ApplicationProvider.getApplicationContext();
    Session previous = app.sessions.get();
    String previousServer = app.sessions.lastServer();
    ChatDatabase previousDb = app.database;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch received = new CountDownLatch(1), respond = new CountDownLatch(1);
    ChatDatabase db = Room.inMemoryDatabaseBuilder(app, ChatDatabase.class).build();
    try (MockWebServer server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
              received.countDown();
              if (!respond.await(10, TimeUnit.SECONDS))
                return new MockResponse().setResponseCode(503);
              return new MockResponse().setResponseCode(401).setBody("{\"message\":\"expired\"}");
            }
          });
      server.start();
      app.database = db;
      JsonObject user = new JsonObject();
      user.addProperty("id", 991);
      user.addProperty("username", "regression");
      String base = server.url("/").toString().replaceAll("/+$", "");
      app.sessions.save(base, "old-token", user);
      Session old = app.sessions.get();
      ChatMessage pending = new ChatMessage();
      pending.owner = old.owner();
      pending.localId = "991:held";
      pending.clientId = "held";
      pending.senderId = 991;
      pending.peerId = 992;
      pending.body = "pause and resume";
      db.messages().put(pending);
      SendWorker worker =
          TestWorkerBuilder.from(app, SendWorker.class, executor)
              .setInputData(new Data.Builder().putString("owner", old.owner()).build())
              .build();
      java.util.concurrent.Future<ListenableWorker.Result> result = executor.submit(worker::doWork);
      assertTrue("send should be in flight", received.await(10, TimeUnit.SECONDS));
      app.sessions.clear();
      app.sessions.save(base, "new-token", user);
      respond.countDown();
      assertEquals(ListenableWorker.Result.success(), result.get(10, TimeUnit.SECONDS));
      assertEquals("QUEUED", db.messages().all(old.owner(), 992).get(0).status);
      assertEquals("new-token", app.sessions.get().token);
    } finally {
      respond.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
      app.database = previousDb;
      db.close();
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
