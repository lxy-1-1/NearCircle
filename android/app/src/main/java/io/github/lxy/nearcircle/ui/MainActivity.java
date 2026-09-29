package io.github.lxy.nearcircle.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.lxy.nearcircle.net.ApiClient;
import io.github.lxy.nearcircle.net.LiveConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements LiveConnection.Listener {
  private final String[] titles = {"消息", "好友", "朋友圈", "我"};
  private int tab = 0, epoch = 0;
  private boolean scrollToNewest;
  private final androidx.activity.result.ActivityResultLauncher<Intent> composeLauncher =
      registerForActivityResult(
          new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK) {
              scrollToNewest = true;
              if (tab == 2) load(false);
            }
          });
  private boolean loading, refreshPending;
  private long nextCursor = 0;
  private TextView title, status, topAction;
  private LinearLayout extras;
  private RecyclerView list;
  private Rows adapter;
  private final NavIcon[] icons = new NavIcon[4];
  private final TextView[] labels = new TextView[4];
  private final List<JsonObject> posts = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LiveConnection live;
  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          if (tab == 0 || tab == 1) load(false);
          handler.postDelayed(this, 20000);
        }
      };

  private static final class Row {
    final String key, type, label;
    final JsonObject value;

    Row(String key, String type, String label, JsonObject value) {
      this.key = key;
      this.type = type;
      this.label = label;
      this.value = value;
    }

    String content() {
      return label + (value == null ? "" : value.toString());
    }
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (!requireSession()) return;
    if (saved != null) tab = saved.getInt("tab", 0);
    LinearLayout shell = Ui.column(this);
    LinearLayout bar = Ui.row(this);
    Ui.pad(bar, 24, 20, 14, 16);
    LinearLayout headings = Ui.column(this);
    TextView wordmark = Ui.text(this, "近友  NEARCIRCLE", 10, Ui.GREEN, true);
    wordmark.setLetterSpacing(.13f);
    headings.addView(wordmark);
    Ui.gap(headings, 8);
    title = Ui.text(this, titles[tab], 30, Ui.INK, true);
    headings.addView(title);
    bar.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));
    topAction = Ui.action(this, "＋");
    Ui.pad(topAction, 0, 0, 0, 0);
    topAction.setTextSize(28);
    topAction.setContentDescription("添加好友或发布朋友圈");
    bar.addView(topAction, Ui.lp(this, 50, 50));
    topAction.setOnClickListener(
        v -> {
          if (tab == 2) composeLauncher.launch(new Intent(this, ComposeActivity.class));
          else select(1);
        });
    shell.addView(bar);
    extras = Ui.column(this);
    shell.addView(extras);
    status = Ui.text(this, "正在连接", 11, Ui.MUTED, false);
    Ui.pad(status, 24, 0, 24, 10);
    status.setOnClickListener(v -> load(false));
    shell.addView(status);
    list = new RecyclerView(this);
    list.setId(io.github.lxy.nearcircle.R.id.main_list);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setItemAnimator(null);
    list.setClipToPadding(false);
    Ui.pad(list, 16, 0, 16, 20);
    adapter = new Rows();
    list.setAdapter(adapter);
    shell.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    shell.addView(Ui.divider(this));
    LinearLayout nav = Ui.row(this);
    nav.setBackgroundColor(Ui.WHITE);
    Ui.pad(nav, 8, 4, 8, 6);
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER);
      Ui.pad(item, 0, 8, 0, 6);
      icons[i] = new NavIcon(this, i);
      item.addView(icons[i], Ui.lp(this, 26, 26));
      Ui.gap(item, 5);
      labels[i] = Ui.text(this, titles[i], 11, Ui.MUTED, false);
      labels[i].setGravity(Gravity.CENTER);
      item.addView(labels[i]);
      nav.addView(item, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1));
      item.setContentDescription(titles[i]);
      item.setOnClickListener(v -> select(index));
    }
    shell.addView(nav);
    root(shell);
    live = new LiveConnection(app, session, this);
    select(tab);
  }

  private void select(int value) {
    tab = value;
    epoch++;
    loading = false;
    refreshPending = false;
    title.setText(titles[tab]);
    extras.removeAllViews();
    topAction.setText(tab == 2 ? "＋" : "＋");
    topAction.setVisibility(tab == 3 ? View.GONE : View.VISIBLE);
    for (int i = 0; i < 4; i++) {
      icons[i].active(i == tab);
      labels[i].setTextColor(i == tab ? Ui.GREEN : Ui.MUTED);
    }
    if (tab == 1) {
      LinearLayout search = Ui.row(this);
      Ui.pad(search, 16, 0, 16, 12);
      EditText query = Ui.input(this, "搜索用户名，认识新朋友");
      query.setTextSize(14);
      search.addView(query, new LinearLayout.LayoutParams(0, -2, 1));
      TextView go = Ui.action(this, "搜索");
      search.addView(go);
      go.setOnClickListener(
          v -> {
            String q = query.getText().toString().trim();
            if (q.length() < 2) {
              toast("请输入至少 2 个字符");
              return;
            }
            int version = epoch;
            request(
                () ->
                    app.api(session)
                        .get("/users?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8.name())),
                data -> {
                  if (tab != 1 || epoch != version) return;
                  List<Row> rows = new ArrayList<>();
                  rows.add(new Row("results", "section", "搜索结果", null));
                  for (JsonElement x : data.getAsJsonArray("items")) {
                    JsonObject user = x.getAsJsonObject();
                    rows.add(new Row("u" + id(user), "search", "", user));
                  }
                  if (rows.size() == 1)
                    rows.add(new Row("empty", "empty", "没有找到这个用户\n试试完整用户名", null));
                  adapter.submitList(rows);
                });
          });
      extras.addView(search);
    }
    if (tab == 2) {
      LinearLayout intro = Ui.row(this);
      Ui.pad(intro, 24, 0, 24, 16);
      TextView tip = Ui.text(this, "朋友的日常", 14, Ui.INK, true);
      intro.addView(tip, new LinearLayout.LayoutParams(0, -2, 1));
      intro.addView(Ui.text(this, "仅自己和好友可见", 11, Ui.MUTED, false));
      extras.addView(intro);
    }
    adapter.submitList(new ArrayList<>());
    status.setText("正在加载…");
    load(false);
  }

  private void load(boolean more) {
    if (session == null) return;
    if (loading) {
      if (!more) refreshPending = true;
      return;
    }
    loading = true;
    int version = epoch, currentTab = tab;
    long cursor = more ? nextCursor : 0;
    long oldestPost =
        currentTab == 2 && !more && !posts.isEmpty() ? id(posts.get(posts.size() - 1)) : 0;
    app.io.execute(
        () -> {
          try {
            ApiClient api = app.api(session);
            JsonObject data, extra = null;
            if (currentTab == 0) data = api.get("/conversations");
            else if (currentTab == 1) {
              data = api.get("/friends");
              extra = api.get("/friend-requests");
            } else if (currentTab == 2) data = loadPosts(api, cursor, oldestPost);
            else data = api.get("/me");
            JsonObject second = extra;
            runOnUiThread(
                () -> {
                  if (isDestroyed() || epoch != version || !app.sessions.matches(session)) return;
                  loading = false;
                  status.setText(currentTab == 2 ? "下拉列表末尾可加载更早动态" : "已同步 · 点此刷新");
                  renderData(currentTab, data, second, more);
                  drainRefresh();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isDestroyed() || epoch != version || !app.sessions.matches(session)) return;
                  loading = false;
                  status.setText("连接失败 · 点此重试");
                  if (adapter.getItemCount() == 0)
                    adapter.submitList(
                        java.util.Collections.singletonList(
                            new Row("error", "empty", "暂时无法加载\n检查服务地址和网络后，点上方重试", null)));
                  if (e instanceof ApiClient.ApiException
                      && ((ApiClient.ApiException) e).code == 401) {
                    refreshPending = false;
                    if (app.logoutIfCurrent(session)) login();
                    return;
                  }
                  drainRefresh();
                });
          }
        });
  }

  private void drainRefresh() {
    if (refreshPending) {
      refreshPending = false;
      load(false);
    }
  }

  /** Refresh the already loaded range so an invalidation does not discard older pages. */
  private JsonObject loadPosts(ApiClient api, long cursor, long oldest) throws java.io.IOException {
    JsonArray items = new JsonArray();
    JsonObject combined = new JsonObject();
    combined.add("items", items);
    for (int page = 0; page < 100; page++) {
      JsonObject data = api.get("/posts?limit=20" + (cursor > 0 ? "&before_id=" + cursor : ""));
      if (oldest == 0) return data;
      JsonArray received = data.getAsJsonArray("items");
      JsonElement next = data.get("next_cursor");
      for (int i = 0; i < received.size(); i++) {
        JsonObject post = received.get(i).getAsJsonObject();
        items.add(post);
        if (id(post) <= oldest) {
          // Stop exactly at the retained boundary, not at the end of the next
          // server page: otherwise every new post silently grows the loaded range.
          if (i + 1 < received.size() || (next != null && !next.isJsonNull()))
            combined.addProperty("next_cursor", id(post));
          else combined.add("next_cursor", com.google.gson.JsonNull.INSTANCE);
          return combined;
        }
      }
      if (next == null || next.isJsonNull()) {
        combined.add("next_cursor", com.google.gson.JsonNull.INSTANCE);
        return combined;
      }
      long before = next.getAsLong();
      if (received.isEmpty() || (cursor > 0 && before >= cursor))
        throw new java.io.IOException("动态分页游标未前进");
      cursor = before;
    }
    throw new java.io.IOException("动态分页超过限制");
  }

  private void renderData(int current, JsonObject data, JsonObject extra, boolean more) {
    List<Row> rows = new ArrayList<>();
    if (current == 0) {
      rows.add(new Row("section", "section", "最近联系", null));
      for (JsonElement x : data.getAsJsonArray("items")) {
        JsonObject item = x.getAsJsonObject();
        rows.add(new Row("c" + id(item.getAsJsonObject("peer")), "conversation", "", item));
      }
      if (rows.size() == 1) rows.add(new Row("empty", "empty", "还没有聊天\n去好友页添加朋友，发出第一声问候", null));
    } else if (current == 1) {
      JsonArray requests = extra.getAsJsonArray("items");
      if (!requests.isEmpty()) {
        rows.add(new Row("requests", "section", "好友申请", null));
        for (JsonElement x : requests) {
          JsonObject item = x.getAsJsonObject();
          rows.add(new Row("r" + id(item), "request", "", item));
        }
      }
      rows.add(
          new Row("friends", "section", "我的好友 · " + data.getAsJsonArray("items").size(), null));
      for (JsonElement x : data.getAsJsonArray("items")) {
        JsonObject item = x.getAsJsonObject();
        rows.add(new Row("f" + id(item), "friend", "", item));
      }
      if (data.getAsJsonArray("items").isEmpty())
        rows.add(new Row("empty", "empty", "好友从一次问候开始\n搜索用户名，发送好友申请", null));
    } else if (current == 2) {
      if (!more) posts.clear();
      for (JsonElement x : data.getAsJsonArray("items")) {
        JsonObject item = x.getAsJsonObject();
        boolean exists = false;
        for (JsonObject p : posts)
          if (id(p) == id(item)) {
            exists = true;
            break;
          }
        if (!exists) posts.add(item);
      }
      nextCursor =
          data.has("next_cursor") && !data.get("next_cursor").isJsonNull()
              ? data.get("next_cursor").getAsLong()
              : 0;
      feedRows(rows);
    } else {
      rows.add(new Row("profile", "profile", "", data));
      rows.add(new Row("space", "section", "我的空间", null));
      rows.add(new Row("edit", "action", "编辑个人资料", data));
      rows.add(new Row("friends-action", "action", "我的好友", null));
      rows.add(new Row("privacy", "info", "朋友圈仅自己和好友可见。聊天记录保存在本机，并从服务端同步。", null));
      rows.add(new Row("server", "info", "当前服务\n" + session.baseUrl, null));
      rows.add(new Row("logout", "action", "退出登录", null));
    }
    adapter.submitList(
        rows,
        () -> {
          if (tab == 2 && scrollToNewest) {
            scrollToNewest = false;
            list.scrollToPosition(0);
          }
        });
  }

  private void feedRows(List<Row> rows) {
    for (JsonObject p : posts) rows.add(new Row("p" + id(p), "post", "", p));
    if (posts.isEmpty()) rows.add(new Row("empty", "empty", "留下一点今天\n发布一条动态，让朋友知道你的近况", null));
    else rows.add(new Row("more", "more", nextCursor > 0 ? "加载更早动态" : "已经看完朋友们的近况", null));
  }

  private void updatedPost(JsonObject post) {
    for (int i = 0; i < posts.size(); i++)
      if (id(posts.get(i)) == id(post)) {
        posts.set(i, post);
        break;
      }
    if (tab == 2) {
      List<Row> rows = new ArrayList<>();
      feedRows(rows);
      adapter.submitList(rows);
    }
  }

  private void chat(JsonObject user) {
    if (id(user) == session.userId) return;
    startActivity(
        new Intent(this, ChatActivity.class)
            .putExtra("peer", id(user))
            .putExtra("name", str(user, "display_name")));
  }

  private View person(JsonObject user, String secondary, String action, Runnable click) {
    LinearLayout row = Ui.row(this);
    Ui.pad(row, 16, 16, 12, 16);
    row.setBackground(Ui.shape(Ui.WHITE, 18, this));
    row.addView(Ui.avatar(this, str(user, "display_name"), id(user), 48));
    LinearLayout words = Ui.column(this);
    Ui.pad(words, 14, 0, 8, 0);
    words.addView(Ui.text(this, str(user, "display_name"), 16, Ui.INK, true));
    Ui.gap(words, 7);
    TextView sub = Ui.text(this, secondary, 12, Ui.MUTED, false);
    sub.setSingleLine(true);
    sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
    words.addView(sub);
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      TextView button = Ui.action(this, action);
      row.addView(button);
      button.setOnClickListener(v -> click.run());
    } else row.setOnClickListener(v -> click.run());
    return row;
  }

  private View render(Row row) {
    JsonObject value = row.value;
    switch (row.type) {
      case "section":
        {
          TextView t = Ui.text(this, row.label, 12, Ui.MUTED, true);
          Ui.pad(t, 8, 18, 8, 12);
          return t;
        }
      case "empty":
        {
          LinearLayout box = Ui.column(this);
          box.setGravity(Gravity.CENTER);
          Ui.pad(box, 20, 56, 20, 48);
          NavIcon icon = new NavIcon(this, tab == 2 ? 2 : 0);
          box.addView(icon, Ui.lp(this, 48, 48));
          Ui.gap(box, 24);
          TextView t = Ui.text(this, row.label, 15, Ui.MUTED, false);
          t.setGravity(Gravity.CENTER);
          t.setLineSpacing(Ui.dp(this, 10), 1);
          box.addView(t);
          return box;
        }
      case "conversation":
        {
          JsonObject peer = value.getAsJsonObject("peer");
          JsonObject last =
              value.has("last_message") && !value.get("last_message").isJsonNull()
                  ? value.getAsJsonObject("last_message")
                  : null;
          LinearLayout item =
              (LinearLayout)
                  person(peer, last == null ? "开始聊天" : str(last, "body"), null, () -> chat(peer));
          LinearLayout trail = Ui.column(this);
          trail.setGravity(Gravity.END);
          trail.addView(
              Ui.text(
                  this,
                  last == null ? "" : Ui.time(last.get("created_at").getAsLong()),
                  10,
                  Ui.MUTED,
                  false));
          int count = value.has("unread_count") ? value.get("unread_count").getAsInt() : 0;
          if (count > 0) {
            Ui.gap(trail, 10);
            TextView badge =
                Ui.text(this, count > 99 ? "99+" : String.valueOf(count), 10, Ui.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.shape(Ui.GREEN, 12, this));
            trail.addView(badge, Ui.lp(this, 24, 22));
          }
          item.addView(trail, Ui.lp(this, 50, -2));
          return item;
        }
      case "friend":
        return person(value, "@" + str(value, "username"), null, () -> chat(value));
      case "search":
        return person(
            value,
            "@" + str(value, "username"),
            "添加",
            () -> {
              JsonObject body = new JsonObject();
              body.addProperty("user_id", id(value));
              request(
                  () -> app.api(session).post("/friend-requests", body),
                  r -> {
                    toast("好友申请已发送");
                    select(1);
                  });
            });
      case "request":
        {
          JsonObject from = value.getAsJsonObject("from_user"),
              to = value.getAsJsonObject("to_user");
          boolean incoming = id(to) == session.userId;
          LinearLayout box = Ui.column(this);
          box.addView(
              person(
                  incoming ? from : to,
                  incoming ? "想添加你为好友" : "等待对方通过",
                  incoming ? "接受" : null,
                  () -> {
                    if (incoming)
                      request(
                          () ->
                              app.api(session)
                                  .post(
                                      "/friend-requests/" + id(value) + "/accept",
                                      new JsonObject()),
                          r -> {
                            toast("已成为好友");
                            load(false);
                          });
                  }));
          if (incoming) {
            TextView reject = Ui.action(this, "忽略这条申请");
            reject.setTextSize(11);
            reject.setTextColor(Ui.MUTED);
            box.addView(reject);
            reject.setOnClickListener(
                v ->
                    request(
                        () ->
                            app.api(session)
                                .post(
                                    "/friend-requests/" + id(value) + "/reject", new JsonObject()),
                        r -> load(false)));
          }
          return box;
        }
      case "post":
        return post(value);
      case "more":
        {
          TextView t = Ui.action(this, row.label);
          t.setTextColor(Ui.MUTED);
          if (nextCursor > 0) t.setOnClickListener(v -> load(true));
          return t;
        }
      case "profile":
        {
          LinearLayout card = Ui.column(this);
          Ui.pad(card, 24, 28, 24, 28);
          card.setBackground(Ui.shape(Ui.PALE, 24, this));
          card.addView(Ui.avatar(this, str(value, "display_name"), id(value), 72));
          Ui.gap(card, 20);
          card.addView(Ui.text(this, str(value, "display_name"), 26, Ui.INK, true));
          Ui.gap(card, 8);
          card.addView(Ui.text(this, "@" + str(value, "username"), 13, Ui.GREEN, false));
          Ui.gap(card, 18);
          card.addView(
              Ui.text(
                  this,
                  str(value, "bio").isEmpty() ? "还没有写个人简介" : str(value, "bio"),
                  14,
                  Ui.MUTED,
                  false));
          return card;
        }
      case "info":
        {
          TextView t = Ui.text(this, row.label, 12, Ui.MUTED, false);
          t.setLineSpacing(Ui.dp(this, 4), 1);
          Ui.pad(t, 10, 10, 10, 10);
          return t;
        }
      case "action":
        {
          TextView t =
              Ui.text(
                  this, row.label + "  ›", 15, row.key.equals("logout") ? Ui.RED : Ui.INK, true);
          Ui.pad(t, 18, 20, 18, 20);
          t.setBackground(Ui.shape(Ui.WHITE, 16, this));
          t.setOnClickListener(
              v -> {
                if (row.key.equals("edit")) editProfile(value);
                else if (row.key.equals("friends-action")) select(1);
                else
                  new AlertDialog.Builder(this)
                      .setTitle("退出当前账号？")
                      .setMessage("待发送消息将暂停，重新登录后可继续发送。")
                      .setNegativeButton("取消", null)
                      .setPositiveButton(
                          "退出",
                          (d, w) -> {
                            app.logout();
                            login();
                          })
                      .show();
              });
          return t;
        }
      default:
        return new View(this);
    }
  }

  private View post(JsonObject post) {
    LinearLayout card = Ui.column(this);
    Ui.pad(card, 18, 18, 18, 16);
    card.setBackground(Ui.shape(Ui.WHITE, 22, this));
    JsonObject author = post.getAsJsonObject("author");
    LinearLayout head = Ui.row(this);
    head.addView(Ui.avatar(this, str(author, "display_name"), id(author), 42));
    LinearLayout name = Ui.column(this);
    Ui.pad(name, 12, 0, 0, 0);
    name.addView(Ui.text(this, str(author, "display_name"), 15, Ui.INK, true));
    Ui.gap(name, 5);
    name.addView(Ui.text(this, Ui.date(post.get("created_at").getAsLong()), 10, Ui.MUTED, false));
    head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    if (id(author) == session.userId) {
      TextView remove = Ui.action(this, "···");
      head.addView(remove);
      remove.setContentDescription("删除自己的动态");
      remove.setOnClickListener(
          v ->
              new AlertDialog.Builder(this)
                  .setItems(
                      new String[] {"删除这条动态"},
                      (d, w) ->
                          new AlertDialog.Builder(this)
                              .setTitle("删除动态？")
                              .setNegativeButton("取消", null)
                              .setPositiveButton(
                                  "删除",
                                  (dialog, which) ->
                                      request(
                                          () -> app.api(session).delete("/posts/" + id(post)),
                                          r -> load(false)))
                              .show())
                  .show());
    } else name.setOnClickListener(v -> chat(author));
    card.addView(head);
    Ui.gap(card, 16);
    TextView content = Ui.text(this, str(post, "body"), 16, Ui.INK, false);
    content.setLineSpacing(Ui.dp(this, 5), 1);
    card.addView(content);
    String media = str(post, "media_id");
    if (!media.isEmpty()) {
      Ui.gap(card, 14);
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setBackground(Ui.shape(Ui.PALE, 14, this));
      image.setClipToOutline(true);
      image.setContentDescription("朋友圈图片");
      card.addView(image, Ui.lp(this, -1, 210));
      MediaLoader.load(app, session, media, image);
      image.setOnClickListener(
          v -> {
            ImageView full = new ImageView(this);
            full.setAdjustViewBounds(true);
            full.setScaleType(ImageView.ScaleType.FIT_CENTER);
            MediaLoader.load(app, session, media, full);
            new AlertDialog.Builder(this).setView(full).setPositiveButton("关闭", null).show();
          });
    }
    Ui.gap(card, 12);
    LinearLayout controls = Ui.row(this);
    TextView likes =
        Ui.action(
            this,
            (post.get("liked").getAsBoolean() ? "♥" : "♡")
                + "  "
                + post.get("like_count").getAsInt());
    controls.addView(likes);
    likes.setContentDescription(post.get("liked").getAsBoolean() ? "取消点赞" : "点赞");
    likes.setOnClickListener(
        v -> {
          JsonObject body = new JsonObject();
          body.addProperty("liked", !post.get("liked").getAsBoolean());
          request(
              () -> app.api(session).put("/posts/" + id(post) + "/like", body), this::updatedPost);
        });
    TextView comment = Ui.action(this, "评论");
    controls.addView(comment);
    comment.setOnClickListener(v -> comment(post));
    card.addView(controls);
    JsonArray comments = post.getAsJsonArray("comments");
    if (comments != null && !comments.isEmpty()) {
      LinearLayout box = Ui.column(this);
      box.setBackground(Ui.shape(Ui.BG, 12, this));
      Ui.pad(box, 12, 10, 12, 8);
      for (JsonElement x : comments) {
        JsonObject c = x.getAsJsonObject();
        TextView line =
            Ui.text(
                this,
                str(c.getAsJsonObject("author"), "display_name") + "：" + str(c, "body"),
                13,
                Ui.INK,
                false);
        line.setLineSpacing(Ui.dp(this, 3), 1);
        Ui.pad(line, 0, 4, 0, 4);
        box.addView(line);
      }
      card.addView(box);
    }
    return card;
  }

  private void comment(JsonObject post) {
    EditText input = Ui.input(this, "写一条评论");
    input.setSingleLine(false);
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
    LinearLayout form = Ui.column(this);
    Ui.pad(form, 20, 8, 20, 8);
    form.addView(input);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("评论")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("发送", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String text = input.getText().toString().trim();
                      if (text.isEmpty()) {
                        input.setError("请输入评论");
                        return;
                      }
                      JsonObject body = new JsonObject();
                      body.addProperty("body", text);
                      request(
                          () -> app.api(session).post("/posts/" + id(post) + "/comments", body),
                          r -> {
                            updatedPost(r);
                            dialog.dismiss();
                          });
                    }));
    dialog.show();
  }

  private void editProfile(JsonObject user) {
    LinearLayout form = Ui.column(this);
    Ui.pad(form, 20, 12, 20, 12);
    EditText name = Ui.input(this, "昵称");
    name.setText(str(user, "display_name"));
    name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(30)});
    EditText bio = Ui.input(this, "个人简介");
    bio.setText(str(user, "bio"));
    bio.setSingleLine(false);
    bio.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(160)});
    form.addView(name);
    Ui.gap(form, 12);
    form.addView(bio);
    new AlertDialog.Builder(this)
        .setTitle("编辑个人资料")
        .setView(form)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存",
            (d, w) -> {
              JsonObject body = new JsonObject();
              body.addProperty("display_name", name.getText().toString().trim());
              body.addProperty("bio", bio.getText().toString().trim());
              request(
                  () -> app.api(session).patch("/me", body),
                  r -> {
                    toast("资料已更新");
                    load(false);
                  });
            })
        .show();
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
    load(false);
    handler.postDelayed(poll, 20000);
    app.io.execute(() -> app.messages.schedule(session));
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacksAndMessages(null);
    if (live != null) live.stop();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle out) {
    super.onSaveInstanceState(out);
    out.putInt("tab", tab);
  }

  @Override
  public void state(String value) {
    if (!loading) status.setText(value + " · 点此刷新");
  }

  @Override
  public void refresh() {
    load(false);
  }

  private final class Rows extends ListAdapter<Row, Holder> {
    Rows() {
      super(
          new DiffUtil.ItemCallback<Row>() {
            public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
              return a.key.equals(b.key);
            }

            public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
              return a.content().equals(b.content());
            }
          });
    }

    @NonNull
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
      LinearLayout root = Ui.column(MainActivity.this);
      root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      return new Holder(root);
    }

    public void onBindViewHolder(@NonNull Holder holder, int position) {
      LinearLayout root = (LinearLayout) holder.itemView;
      root.removeAllViews();
      Ui.pad(root, 0, 0, 0, 8);
      root.addView(render(getItem(position)), Ui.lp(MainActivity.this, -1, -2));
    }
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    Holder(View view) {
      super(view);
    }
  }
}
