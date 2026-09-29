package io.github.lxy.nearcircle;

import android.app.Application;
import androidx.room.Room;
import androidx.work.WorkManager;
import io.github.lxy.nearcircle.data.ChatDatabase;
import io.github.lxy.nearcircle.data.MessageRepository;
import io.github.lxy.nearcircle.data.Session;
import io.github.lxy.nearcircle.data.SessionStore;
import io.github.lxy.nearcircle.net.ApiClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NearApp extends Application {
  public final ExecutorService io = Executors.newFixedThreadPool(4);
  public SessionStore sessions;
  public ChatDatabase database;
  public MessageRepository messages;

  @Override
  public void onCreate() {
    super.onCreate();
    sessions = new SessionStore(this);
    database = Room.databaseBuilder(this, ChatDatabase.class, "near-chat.db").build();
    messages = new MessageRepository(this);
  }

  public ApiClient api(Session s) {
    return new ApiClient(s.baseUrl, s.token);
  }

  public void logout() {
    logoutIfCurrent(sessions.get());
  }

  public boolean logoutIfCurrent(Session old) {
    synchronized (sessions) {
      if (!sessions.clearIfCurrent(old)) return false;
      // Queue cancellation before another login can schedule work for this owner.
      WorkManager.getInstance(this).cancelUniqueWork(MessageRepository.workName(old.owner()));
    }
    io.execute(
        () -> {
          try {
            api(old).post("/auth/logout", new com.google.gson.JsonObject());
          } catch (Exception ignored) {
            /* local sign-out is immediate even offline */
          }
        });
    return true;
  }
}
