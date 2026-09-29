package io.github.lxy.nearcircle.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.data.ChatMessage;
import io.github.lxy.nearcircle.net.ApiClient;
import io.github.lxy.nearcircle.net.LiveConnection;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatActivity extends BaseActivity implements LiveConnection.Listener {
  private long peer;
  private String name;
  private TextView connection;
  private EditText input;
  private TextView send;
  private RecyclerView list;
  private ChatAdapter adapter;
  private LiveConnection live;
  private final AtomicBoolean syncing = new AtomicBoolean(false);
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          refresh();
          handler.postDelayed(this, 15000);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (!requireSession()) return;
    peer = getIntent().getLongExtra("peer", 0);
    name = getIntent().getStringExtra("name");
    if (peer <= 0) {
      finish();
      return;
    }
    if (name == null) name = "好友";
    LinearLayout shell = Ui.column(this);
    LinearLayout bar = Ui.row(this);
    Ui.pad(bar, 8, 8, 16, 12);
    TextView back = Ui.action(this, "‹");
    back.setTextSize(34);
    Ui.pad(back, 0, 0, 0, 0);
    bar.addView(back, Ui.lp(this, 44, 52));
    back.setContentDescription("返回消息列表");
    back.setOnClickListener(v -> finish());
    bar.addView(Ui.avatar(this, name, peer, 44));
    LinearLayout labels = Ui.column(this);
    Ui.pad(labels, 12, 0, 0, 0);
    labels.addView(Ui.text(this, name, 19, Ui.INK, true));
    Ui.gap(labels, 6);
    connection = Ui.text(this, "正在同步消息", 11, Ui.MUTED, false);
    labels.addView(connection);
    bar.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    shell.addView(bar);
    shell.addView(Ui.divider(this));
    list = new RecyclerView(this);
    list.setId(io.github.lxy.nearcircle.R.id.chat_list);
    list.setClipToPadding(false);
    Ui.pad(list, 16, 16, 16, 16);
    LinearLayoutManager manager = new LinearLayoutManager(this);
    manager.setStackFromEnd(true);
    list.setLayoutManager(manager);
    adapter = new ChatAdapter();
    list.setAdapter(adapter);
    list.setItemAnimator(null);
    shell.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout composer = Ui.row(this);
    Ui.pad(composer, 16, 10, 12, 10);
    composer.setBackgroundColor(Ui.WHITE);
    input = Ui.input(this, "发消息…");
    input.setId(io.github.lxy.nearcircle.R.id.chat_input);
    input.setSingleLine(false);
    input.setMaxLines(5);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2000)});
    input.setBackground(Ui.shape(Ui.BG, 18, this));
    Ui.pad(input, 16, 12, 16, 12);
    composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
    send = Ui.action(this, "发送");
    composer.addView(send, Ui.lp(this, 64, 48));
    shell.addView(composer);
    send.setOnClickListener(v -> send());
    root(shell);
    app.database
        .messages()
        .observe(session.owner(), peer)
        .observe(
            this,
            rows -> {
              boolean atEnd = manager.findLastVisibleItemPosition() >= adapter.getItemCount() - 3;
              adapter.submitList(
                  new ArrayList<>(rows),
                  () -> {
                    if (atEnd && !rows.isEmpty()) list.scrollToPosition(rows.size() - 1);
                  });
            });
    live = new LiveConnection(app, session, this);
  }

  private void send() {
    String body = input.getText().toString().trim();
    if (body.isEmpty()) return;
    send.setEnabled(false);
    app.io.execute(
        () -> {
          try {
            app.messages.enqueue(session, peer, body);
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  if (input.getText().toString().trim().equals(body)) input.setText("");
                  send.setEnabled(true);
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  send.setEnabled(true);
                  toast("本地保存失败，请重试");
                });
          }
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (live == null) return;
    if (!app.sessions.matches(session)) {
      login();
      return;
    }
    live.start();
    handler.post(poll);
    app.io.execute(() -> app.messages.schedule(session));
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacksAndMessages(null);
    if (live != null) live.stop();
  }

  @Override
  public void state(String value) {
    connection.setText(value);
  }

  @Override
  public void refresh() {
    if (!syncing.compareAndSet(false, true)) return;
    app.io.execute(
        () -> {
          try {
            app.messages.sync(session, peer);
            if (!app.sessions.matches(session)) return;
            long through = 0;
            for (ChatMessage m : app.database.messages().all(session.owner(), peer))
              if (m.senderId == peer) through = Math.max(through, m.serverId);
            if (through > 0) {
              JsonObject read = new JsonObject();
              read.addProperty("through_id", through);
              app.api(session).post("/messages/" + peer + "/read", read);
            }
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed() || !app.sessions.matches(session)) return;
                  if (e instanceof ApiClient.ApiException
                      && ((ApiClient.ApiException) e).code == 401) {
                    if (app.logoutIfCurrent(session)) login();
                  } else connection.setText("暂时离线 · 消息已保存在本机");
                });
          } finally {
            syncing.set(false);
          }
        });
  }

  private final class ChatAdapter extends ListAdapter<ChatMessage, ChatHolder> {
    ChatAdapter() {
      super(
          new DiffUtil.ItemCallback<ChatMessage>() {
            public boolean areItemsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
              return a.localId.equals(b.localId);
            }

            public boolean areContentsTheSame(@NonNull ChatMessage a, @NonNull ChatMessage b) {
              return a.body.equals(b.body)
                  && a.status.equals(b.status)
                  && a.error.equals(b.error)
                  && a.createdAt == b.createdAt;
            }
          });
    }

    @NonNull
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
      LinearLayout root = Ui.column(parent.getContext());
      root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      return new ChatHolder(root);
    }

    public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
      ChatMessage m = getItem(position);
      boolean mine = m.senderId == session.userId;
      LinearLayout root = (LinearLayout) holder.itemView;
      root.removeAllViews();
      root.setGravity(mine ? Gravity.END : Gravity.START);
      Ui.pad(root, 0, 5, 0, 5);
      if (position == 0 || m.createdAt - getItem(position - 1).createdAt > 300000) {
        TextView time = Ui.text(ChatActivity.this, Ui.date(m.createdAt), 11, Ui.MUTED, false);
        time.setGravity(Gravity.CENTER);
        Ui.pad(time, 0, 12, 0, 12);
        root.addView(time, Ui.lp(ChatActivity.this, -1, -2));
      }
      LinearLayout row = Ui.row(ChatActivity.this);
      row.setGravity(Gravity.TOP);
      if (!mine) {
        TextView avatar = Ui.avatar(ChatActivity.this, name, peer, 34);
        row.addView(avatar);
      }
      TextView bubble = Ui.text(ChatActivity.this, m.body, 16, mine ? Ui.WHITE : Ui.INK, false);
      bubble.setTextIsSelectable(true);
      bubble.setLineSpacing(Ui.dp(ChatActivity.this, 4), 1);
      bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * .70));
      bubble.setBackground(Ui.shape(mine ? Ui.GREEN : Ui.WHITE, 18, ChatActivity.this));
      Ui.pad(bubble, 15, 12, 15, 12);
      LinearLayout.LayoutParams b = Ui.lp(ChatActivity.this, -2, -2);
      if (!mine) b.leftMargin = Ui.dp(ChatActivity.this, 8);
      row.addView(bubble, b);
      root.addView(row, Ui.lp(ChatActivity.this, -2, -2));
      if (mine) {
        String label =
            "QUEUED".equals(m.status)
                ? "等待发送 · 联网后自动重试"
                : "FAILED".equals(m.status) ? "未发送 · 点按重试" : "已发送  " + Ui.time(m.createdAt);
        TextView status =
            Ui.text(
                ChatActivity.this, label, 10, "FAILED".equals(m.status) ? Ui.RED : Ui.MUTED, false);
        Ui.pad(status, 8, 6, 4, 4);
        status.setGravity(Gravity.END);
        root.addView(status);
        if (!"SENT".equals(m.status)) {
          status.setMinHeight(Ui.dp(ChatActivity.this, 40));
          status.setOnClickListener(v -> app.io.execute(() -> app.messages.retry(session, m)));
        }
      }
    }
  }

  private static final class ChatHolder extends RecyclerView.ViewHolder {
    ChatHolder(View v) {
      super(v);
    }
  }
}
