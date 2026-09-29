package com.nearcircle;

import java.sql.Statement;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SocialService {
  final JdbcTemplate db;
  final Security security;
  final ApplicationEventPublisher events;

  public record Refresh(Set<Long> users) {}

  public SocialService(JdbcTemplate db, Security security, ApplicationEventPublisher events) {
    this.db = db;
    this.security = security;
    this.events = events;
  }

  static long num(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  static Map<String, Object> map(Object... values) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
    return result;
  }

  List<Map<String, Object>> rows(String sql, Object... args) {
    return db.query(
        sql,
        (rs, n) -> {
          var m = new LinkedHashMap<String, Object>();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
            m.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
          return m;
        },
        args);
  }

  Map<String, Object> one(String sql, Object... args) {
    var found = rows(sql, args);
    if (found.isEmpty()) throw new ApiError(404, "内容不存在");
    return found.get(0);
  }

  long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    db.update(
        conn -> {
          var p = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        keys);
    return keys.getKey().longValue();
  }

  public Map<String, Object> user(long id) {
    return one("SELECT id,username,display_name,bio FROM users WHERE id=?", id);
  }

  public Map<String, Object> register(String username, String password, String name) {
    username = username.toLowerCase(Locale.ROOT);
    if (!username.matches("[a-z0-9_]{3,32}")) throw new ApiError(400, "用户名需为 3–32 位字母、数字或下划线");
    if (password.length() < 8 || password.length() > 128) throw new ApiError(400, "密码需为 8–128 个字符");
    long id;
    try {
      id =
          insert(
              "INSERT INTO users(username,display_name,password_hash) VALUES(?,?,?)",
              username,
              name,
              Security.password(password));
    } catch (DuplicateKeyException e) {
      throw new ApiError(409, "用户名已存在");
    }
    return map("token", security.token(id), "user", user(id));
  }

  public Map<String, Object> login(String username, String password) {
    var found =
        rows(
            "SELECT id,password_hash FROM users WHERE username=?",
            username.toLowerCase(Locale.ROOT));
    if (found.isEmpty() || !Security.matches(password, (String) found.get(0).get("password_hash")))
      throw new ApiError(401, "用户名或密码错误");
    long id = num(found.get(0), "id");
    return map("token", security.token(id), "user", user(id));
  }

  public Map<String, Object> profile(long id, String name, String bio) {
    db.update("UPDATE users SET display_name=?,bio=? WHERE id=?", name, bio, id);
    refreshCircle(id);
    return user(id);
  }

  public List<Map<String, Object>> search(long id, String q) {
    return rows(
        "SELECT id,username,display_name,bio FROM users WHERE id<>? AND"
            + " (LOCATE(LOWER(?),LOWER(username))>0 OR LOCATE(LOWER(?),LOWER(display_name))>0)"
            + " ORDER BY username LIMIT 20",
        id,
        q,
        q);
  }

  public List<Map<String, Object>> friends(long id) {
    return rows(
        "SELECT u.id,u.username,u.display_name,u.bio FROM users u JOIN friendships f ON"
            + " f.friend_id=u.id WHERE f.user_id=? ORDER BY u.display_name",
        id);
  }

  boolean friend(long a, long b) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM friendships WHERE user_id=? AND friend_id=?", Integer.class, a, b)
        > 0;
  }

  void requireFriend(long a, long b) {
    if (!friend(a, b)) throw new ApiError(403, "仅好友可以访问此会话");
  }

  void lockUser(long id) {
    one("SELECT id FROM users WHERE id=? FOR UPDATE", id);
  }

  public void request(long from, long to) {
    if (from == to) throw new ApiError(400, "不能添加自己");
    lockUser(Math.min(from, to));
    lockUser(Math.max(from, to));
    if (friend(from, to)) throw new ApiError(409, "你们已经是好友");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM friend_requests WHERE status='pending' AND ((from_id=? AND"
                + " to_id=?) OR (from_id=? AND to_id=?))",
            Integer.class,
            from,
            to,
            to,
            from)
        > 0) throw new ApiError(409, "已有待处理的好友申请");
    db.update(
        "INSERT INTO friend_requests(from_id,to_id,status,created_at) VALUES(?,?,'pending',?)",
        from,
        to,
        System.currentTimeMillis());
    refresh(from, to);
  }

  public List<Map<String, Object>> requests(long id) {
    return rows(
            "SELECT * FROM friend_requests WHERE (from_id=? OR to_id=?) AND status='pending' ORDER"
                + " BY id DESC",
            id,
            id)
        .stream()
        .map(
            r ->
                map(
                    "id",
                    r.get("id"),
                    "from_user",
                    user(num(r, "from_id")),
                    "to_user",
                    user(num(r, "to_id")),
                    "status",
                    r.get("status"),
                    "created_at",
                    r.get("created_at")))
        .toList();
  }

  public void decide(long id, long request, boolean accept) {
    var r = one("SELECT * FROM friend_requests WHERE id=? FOR UPDATE", request);
    if (num(r, "to_id") != id) throw new ApiError(403, "只有接收者可以处理申请");
    if (!r.get("status").equals("pending")) throw new ApiError(409, "申请已经处理");
    long from = num(r, "from_id");
    db.update(
        "UPDATE friend_requests SET status=? WHERE id=?",
        accept ? "accepted" : "rejected",
        request);
    if (accept) {
      db.update("MERGE INTO friendships KEY(user_id,friend_id) VALUES(?,?)", from, id);
      db.update("MERGE INTO friendships KEY(user_id,friend_id) VALUES(?,?)", id, from);
    }
    refresh(from, id);
  }

  static final String MESSAGE_COLUMNS = "id,client_id,sender_id,recipient_id,body,created_at";

  public Map<String, Object> send(long from, long to, String client, String body) {
    requireFriend(from, to);
    // Both directions share the same lock order: a later ID cannot commit
    // before an earlier message in this conversation and evade after_id.
    lockUser(Math.min(from, to));
    lockUser(Math.max(from, to));
    var old =
        rows(
            "SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE sender_id=? AND client_id=?",
            from,
            client);
    if (!old.isEmpty()) {
      var r = old.get(0);
      if (num(r, "recipient_id") != to || !body.equals(r.get("body")))
        throw new ApiError(409, "client_id 已被不同内容使用");
      return r;
    }
    long id =
        insert(
            "INSERT INTO messages(client_id,sender_id,recipient_id,body,created_at)"
                + " VALUES(?,?,?,?,?)",
            client,
            from,
            to,
            body,
            System.currentTimeMillis());
    refresh(from, to);
    return one("SELECT " + MESSAGE_COLUMNS + " FROM messages WHERE id=?", id);
  }

  public Map<String, Object> messages(long me, long peer, long after, int limit) {
    requireFriend(me, peer);
    var all =
        rows(
            "SELECT "
                + MESSAGE_COLUMNS
                + " FROM messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND"
                + " recipient_id=?)) AND id>? ORDER BY id LIMIT ?",
            me,
            peer,
            peer,
            me,
            after,
            limit + 1);
    return map(
        "items", all.subList(0, Math.min(limit, all.size())), "has_more", all.size() > limit);
  }

  public void read(long me, long peer, long through) {
    requireFriend(me, peer);
    int changed =
        db.update(
            "UPDATE messages SET read_at=? WHERE sender_id=? AND recipient_id=? AND id<=? AND"
                + " read_at IS NULL",
            System.currentTimeMillis(),
            peer,
            me,
            through);
    if (changed > 0) refresh(me);
  }

  public List<Map<String, Object>> conversations(long me) {
    var result = new ArrayList<Map<String, Object>>();
    for (var peer : friends(me)) {
      long id = num(peer, "id");
      var last =
          rows(
              "SELECT "
                  + MESSAGE_COLUMNS
                  + " FROM messages WHERE (sender_id=? AND recipient_id=?) OR (sender_id=? AND"
                  + " recipient_id=?) ORDER BY id DESC LIMIT 1",
              me,
              id,
              id,
              me);
      result.add(
          map(
              "peer",
              peer,
              "last_message",
              last.isEmpty() ? null : last.get(0),
              "unread_count",
              db.queryForObject(
                  "SELECT COUNT(*) FROM messages WHERE sender_id=? AND recipient_id=? AND read_at"
                      + " IS NULL",
                  Long.class,
                  id,
                  me)));
    }
    result.sort(
        Comparator.comparingLong(
                (Map<String, Object> r) ->
                    r.get("last_message") == null
                        ? 0
                        : num((Map<String, Object>) r.get("last_message"), "id"))
            .reversed());
    return result;
  }

  public String upload(long owner, byte[] content) {
    String id = UUID.randomUUID().toString();
    db.update("INSERT INTO media(id,owner_id,content) VALUES(?,?,?)", id, owner, content);
    return id;
  }

  public byte[] media(long me, String id) {
    var info = one("SELECT owner_id FROM media WHERE id=?", id);
    long owner = num(info, "owner_id");
    if (owner != me
        && (!friend(me, owner)
            || db.queryForObject(
                    "SELECT COUNT(*) FROM posts WHERE media_id=? AND author_id=?",
                    Integer.class,
                    id,
                    owner)
                == 0)) throw new ApiError(403, "没有权限查看图片");
    return db.queryForObject("SELECT content FROM media WHERE id=?", byte[].class, id);
  }

  void visible(long me, Map<String, Object> post) {
    if (num(post, "author_id") != me && !friend(me, num(post, "author_id")))
      throw new ApiError(403, "仅好友可以查看动态");
  }

  Map<String, Object> post(long me, long id) {
    var p = one("SELECT * FROM posts WHERE id=?", id);
    visible(me, p);
    var comments =
        rows("SELECT * FROM comments WHERE post_id=? ORDER BY id", id).stream()
            .map(
                c ->
                    map(
                        "id",
                        c.get("id"),
                        "author",
                        user(num(c, "author_id")),
                        "body",
                        c.get("body"),
                        "created_at",
                        c.get("created_at")))
            .toList();
    return map(
        "id",
        id,
        "author",
        user(num(p, "author_id")),
        "body",
        p.get("body"),
        "media_id",
        p.get("media_id"),
        "created_at",
        p.get("created_at"),
        "liked",
        db.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id=? AND user_id=?",
                Integer.class,
                id,
                me)
            > 0,
        "like_count",
        db.queryForObject("SELECT COUNT(*) FROM post_likes WHERE post_id=?", Long.class, id),
        "comments",
        comments);
  }

  public Map<String, Object> createPost(long me, String body, String media) {
    if (media != null && num(one("SELECT owner_id FROM media WHERE id=?", media), "owner_id") != me)
      throw new ApiError(403, "只能使用自己上传的图片");
    long id =
        insert(
            "INSERT INTO posts(author_id,body,media_id,created_at) VALUES(?,?,?,?)",
            me,
            body,
            media,
            System.currentTimeMillis());
    refreshCircle(me);
    return post(me, id);
  }

  public Map<String, Object> posts(long me, long before, int limit) {
    var ids =
        db.query(
            "SELECT id FROM posts WHERE id<? AND (author_id=? OR author_id IN (SELECT friend_id"
                + " FROM friendships WHERE user_id=?)) ORDER BY id DESC LIMIT ?",
            (rs, n) -> rs.getLong(1),
            before,
            me,
            me,
            limit + 1);
    boolean more = ids.size() > limit;
    var page = ids.subList(0, Math.min(ids.size(), limit));
    return map(
        "items",
        page.stream().map(id -> post(me, id)).toList(),
        "next_cursor",
        more ? page.get(page.size() - 1) : null);
  }

  public void delete(long me, long id) {
    var p = one("SELECT * FROM posts WHERE id=? FOR UPDATE", id);
    if (num(p, "author_id") != me) throw new ApiError(403, "只能删除自己的动态");
    db.update("DELETE FROM posts WHERE id=?", id);
    refreshCircle(me);
  }

  public Map<String, Object> like(long me, long id, boolean liked) {
    var p = one("SELECT * FROM posts WHERE id=? FOR UPDATE", id);
    visible(me, p);
    if (liked) db.update("MERGE INTO post_likes KEY(post_id,user_id) VALUES(?,?)", id, me);
    else db.update("DELETE FROM post_likes WHERE post_id=? AND user_id=?", id, me);
    refreshCircle(num(p, "author_id"));
    return post(me, id);
  }

  public Map<String, Object> comment(long me, long id, String body) {
    var p = one("SELECT * FROM posts WHERE id=? FOR UPDATE", id);
    visible(me, p);
    db.update(
        "INSERT INTO comments(post_id,author_id,body,created_at) VALUES(?,?,?,?)",
        id,
        me,
        body,
        System.currentTimeMillis());
    refreshCircle(num(p, "author_id"));
    return post(me, id);
  }

  void refresh(long... ids) {
    var users = new HashSet<Long>();
    for (long id : ids) users.add(id);
    events.publishEvent(new Refresh(users));
  }

  void refreshCircle(long id) {
    var users = new HashSet<Long>();
    users.add(id);
    for (var f : friends(id)) users.add(num(f, "id"));
    events.publishEvent(new Refresh(users));
  }
}
