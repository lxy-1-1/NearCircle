package io.github.lxy.nearcircle.net;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.NearApp;
import io.github.lxy.nearcircle.data.Session;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Foreground invalidation channel. HTTP catch-up remains the source of truth. */
public final class LiveConnection {
  public interface Listener {
    void refresh();

    void state(String value);
  }

  private final NearApp app;
  private final Session session;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private WebSocket socket;
  private boolean stopped = true;
  private long generation = 0;
  private int failures = 0;

  public LiveConnection(NearApp app, Session session, Listener listener) {
    this.app = app;
    this.session = session;
    this.listener = listener;
  }

  public void start() {
    stopped = false;
    connect();
  }

  public void stop() {
    stopped = true;
    generation++;
    main.removeCallbacksAndMessages(null);
    if (socket != null) {
      socket.cancel();
      socket = null;
    }
  }

  private void connect() {
    if (stopped || !app.sessions.matches(session)) return;
    long g = ++generation;
    listener.state("正在连接");
    app.io.execute(
        () -> {
          try {
            String ticket =
                app.api(session).post("/ws-ticket", new JsonObject()).get("ticket").getAsString();
            main.post(
                () -> {
                  if (stopped || g != generation) return;
                  socket =
                      ApiClient.HTTP.newWebSocket(
                          new Request.Builder().url(app.api(session).socketUrl(ticket)).build(),
                          new WebSocketListener() {
                            @Override
                            public void onOpen(WebSocket ws, Response r) {
                              main.post(
                                  () -> {
                                    if (stopped || g != generation) return;
                                    failures = 0;
                                    listener.state("已连接");
                                    listener.refresh();
                                  });
                            }

                            @Override
                            public void onMessage(WebSocket ws, String text) {
                              main.post(
                                  () -> {
                                    if (!stopped && g == generation) listener.refresh();
                                  });
                            }

                            @Override
                            public void onFailure(WebSocket ws, Throwable t, Response r) {
                              main.post(() -> lost(g));
                            }

                            @Override
                            public void onClosed(WebSocket ws, int code, String reason) {
                              main.post(() -> lost(g));
                            }

                            @Override
                            public void onClosing(WebSocket ws, int code, String reason) {
                              ws.close(code, reason);
                            }
                          });
                });
          } catch (Exception e) {
            main.post(() -> lost(g));
          }
        });
  }

  private void lost(long g) {
    if (stopped || g != generation) return;
    listener.state("连接中断 · 自动重连");
    int delay = Math.min(30000, 1000 * (1 << Math.min(failures++, 5)));
    main.postDelayed(this::connect, delay);
  }
}
