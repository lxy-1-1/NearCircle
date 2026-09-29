package io.github.lxy.nearcircle.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.net.ApiClient;
import java.io.IOException;

public final class SendWorker extends Worker {
  public SendWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  @NonNull
  @Override
  public Result doWork() {
    NearApp app = (NearApp) getApplicationContext();
    Session session = app.sessions.get();
    if (session == null || !session.owner().equals(getInputData().getString("owner")))
      return Result.success();
    MessageDao dao = app.database.messages();
    for (int i = 0; i < 100 && !isStopped() && app.sessions.matches(session); i++) {
      ChatMessage pending = dao.next(session.owner());
      if (pending == null) return Result.success();
      try {
        JsonObject body = new JsonObject();
        body.addProperty("client_id", pending.clientId);
        body.addProperty("body", pending.body);
        ChatMessage accepted =
            app.messages.parse(
                session,
                pending.peerId,
                app.api(session).post("/messages/" + pending.peerId, body));
        if (!accepted.clientId.equals(pending.clientId)
            || accepted.senderId != session.userId
            || !accepted.body.equals(pending.body)) throw new IOException("服务器确认与发送内容不一致");
        synchronized (app.sessions) {
          if (isStopped() || !app.sessions.matches(session)) return Result.success();
          dao.put(accepted);
        }
      } catch (ApiClient.ApiException e) {
        if (e.retryable()) {
          if (!stateIfCurrent(app, session, pending, "QUEUED", "等待网络恢复")) return Result.success();
          return Result.retry();
        }
        if (!stateIfCurrent(app, session, pending, "FAILED", e.getMessage()))
          return Result.success();
        if (e.code == 401) return Result.failure();
      } catch (IOException e) {
        if (!stateIfCurrent(app, session, pending, "QUEUED", "等待网络恢复")) return Result.success();
        return Result.retry();
      }
    }
    return app.sessions.matches(session) && !isStopped() ? Result.retry() : Result.success();
  }

  private boolean stateIfCurrent(
      NearApp app, Session session, ChatMessage message, String status, String error) {
    // Serialize the session check and mutation with logout/login. Cancellation alone
    // does not interrupt an OkHttp call already waiting for its response.
    synchronized (app.sessions) {
      if (isStopped() || !app.sessions.matches(session)) return false;
      app.database.messages().state(session.owner(), message.localId, status, error);
      return true;
    }
  }
}
