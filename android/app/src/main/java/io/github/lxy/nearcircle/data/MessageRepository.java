package io.github.lxy.nearcircle.data;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class MessageRepository {
  private final NearApp app;

  public MessageRepository(NearApp app) {
    this.app = app;
  }

  public static String workName(String owner) {
    return "outbox:" + owner;
  }

  public void enqueue(Session session, long peer, String text) {
    if (!app.sessions.matches(session)) return;
    ChatMessage m = new ChatMessage();
    m.owner = session.owner();
    m.clientId = UUID.randomUUID().toString();
    m.localId = session.userId + ":" + m.clientId;
    m.peerId = peer;
    m.senderId = session.userId;
    m.body = text;
    m.createdAt = System.currentTimeMillis();
    app.database.messages().put(m);
    schedule(session);
  }

  public void schedule(Session session) {
    if (!app.sessions.matches(session)) return;
    OneTimeWorkRequest work =
        new OneTimeWorkRequest.Builder(SendWorker.class)
            .setInputData(new Data.Builder().putString("owner", session.owner()).build())
            .setConstraints(
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build();
    // APPEND_OR_REPLACE avoids the final-empty-check / KEEP lost-wakeup race.
    WorkManager.getInstance(app)
        .enqueueUniqueWork(workName(session.owner()), ExistingWorkPolicy.APPEND_OR_REPLACE, work);
  }

  public void retry(Session s, ChatMessage message) {
    app.database.messages().state(s.owner(), message.localId, "QUEUED", "");
    schedule(s);
  }

  public ChatMessage parse(Session s, long peer, JsonObject value) throws IOException {
    try {
      long sender = value.get("sender_id").getAsLong(),
          recipient = value.get("recipient_id").getAsLong();
      if (!((sender == s.userId && recipient == peer) || (sender == peer && recipient == s.userId)))
        throw new IOException("消息不属于当前会话");
      ChatMessage m = new ChatMessage();
      m.owner = s.owner();
      m.clientId = value.get("client_id").getAsString();
      m.localId = sender + ":" + m.clientId;
      m.serverId = value.get("id").getAsLong();
      m.peerId = peer;
      m.senderId = sender;
      m.body = value.get("body").getAsString();
      m.createdAt = value.get("created_at").getAsLong();
      m.status = "SENT";
      if (m.serverId <= 0 || m.clientId.isEmpty()) throw new IOException("无效消息确认");
      return m;
    } catch (RuntimeException e) {
      throw new IOException("消息响应格式不正确", e);
    }
  }

  /**
   * Cursor advances only after a complete HTTP page is committed, never from a send
   * acknowledgement.
   */
  public synchronized void sync(Session session, long peer) throws IOException {
    MessageDao dao = app.database.messages();
    Long value = dao.cursorValue(session.owner(), peer);
    long cursor = value == null ? 0 : value;
    for (int page = 0; page < 1000 && app.sessions.matches(session); page++) {
      JsonObject result =
          app.api(session).get("/messages/" + peer + "?after_id=" + cursor + "&limit=100");
      java.util.List<ChatMessage> rows = new java.util.ArrayList<>();
      long last = cursor;
      for (JsonElement item : result.getAsJsonArray("items")) {
        ChatMessage m = parse(session, peer, item.getAsJsonObject());
        if (m.serverId <= last) throw new IOException("消息游标没有递增");
        last = m.serverId;
        rows.add(m);
      }
      boolean more = result.has("has_more") && result.get("has_more").getAsBoolean();
      if (more && rows.isEmpty()) throw new IOException("无效分页响应");
      if (!app.sessions.matches(session)) return;
      SyncCursor checkpoint = new SyncCursor();
      checkpoint.owner = session.owner();
      checkpoint.peerId = peer;
      checkpoint.lastId = last;
      app.database.runInTransaction(
          () -> {
            for (ChatMessage m : rows) dao.put(m);
            dao.checkpoint(checkpoint);
          });
      cursor = last;
      if (!more) return;
    }
  }
}
