package com.nearcircle;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final SocialService service;
  private final Security auth;
  private final RefreshSocket socket;

  public ApiController(SocialService service, Security auth, RefreshSocket socket) {
    this.service = service;
    this.auth = auth;
    this.socket = socket;
  }

  long me(HttpServletRequest request) {
    return auth.require(request.getHeader("Authorization"));
  }

  static void fields(Map<String, Object> body, String... allowed) {
    if (body == null || !Set.of(allowed).containsAll(body.keySet()))
      throw new ApiError(400, "请求包含不支持的字段");
  }

  static String string(Map<String, Object> body, String key, int min, int max, boolean trim) {
    Object raw = body.get(key);
    if (!(raw instanceof String)) throw new ApiError(400, key + " 必须为文本");
    String value = (String) raw;
    if (trim) value = value.strip();
    if (value.length() < min || value.length() > max)
      throw new ApiError(400, key + " 长度应为 " + min + "–" + max);
    return value;
  }

  static long number(Map<String, Object> body, String key) {
    Object raw = body.get(key);
    if (!(raw instanceof Integer) && !(raw instanceof Long))
      throw new ApiError(400, key + " 必须为整数");
    long n = ((Number) raw).longValue();
    if (n < 0) throw new ApiError(400, key + " 不能为负数");
    return n;
  }

  static void page(long cursor, int limit, int maximum) {
    if (cursor < 0 || limit < 1 || limit > maximum) throw new ApiError(400, "分页参数不正确");
  }

  static Map<String, Object> items(Object items) {
    return Map.of("items", items);
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @PostMapping("/auth/register")
  Object register(@RequestBody Map<String, Object> b) {
    fields(b, "username", "password", "display_name");
    return service.register(
        string(b, "username", 3, 32, true),
        string(b, "password", 8, 128, false),
        string(b, "display_name", 1, 40, true));
  }

  @PostMapping("/auth/login")
  Object login(@RequestBody Map<String, Object> b) {
    fields(b, "username", "password");
    return service.login(string(b, "username", 1, 32, true), string(b, "password", 1, 128, false));
  }

  @PostMapping("/auth/logout")
  Object logout(HttpServletRequest r) {
    me(r);
    String header = r.getHeader("Authorization");
    auth.logout(header);
    socket.revoke(auth.bearer(header));
    return Map.of();
  }

  @GetMapping("/me")
  Object profile(HttpServletRequest r) {
    return service.user(me(r));
  }

  @PatchMapping("/me")
  Object update(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    long id = me(r);
    fields(b, "display_name", "bio");
    var old = service.user(id);
    return service.profile(
        id,
        b.containsKey("display_name")
            ? string(b, "display_name", 1, 40, true)
            : (String) old.get("display_name"),
        b.containsKey("bio") ? string(b, "bio", 0, 200, true) : (String) old.get("bio"));
  }

  @GetMapping("/users")
  Object users(HttpServletRequest r, @RequestParam String q) {
    long id = me(r);
    q = q.strip();
    if (q.length() < 2 || q.length() > 40) throw new ApiError(400, "搜索词应为 2–40 个字符");
    return items(service.search(id, q));
  }

  @GetMapping("/friends")
  Object friends(HttpServletRequest r) {
    return items(service.friends(me(r)));
  }

  @GetMapping("/friend-requests")
  Object requests(HttpServletRequest r) {
    return items(service.requests(me(r)));
  }

  @PostMapping("/friend-requests")
  Object request(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    long id = me(r);
    fields(b, "user_id");
    service.request(id, number(b, "user_id"));
    return Map.of();
  }

  @PostMapping("/friend-requests/{id}/accept")
  Object accept(HttpServletRequest r, @PathVariable long id) {
    service.decide(me(r), id, true);
    return Map.of();
  }

  @PostMapping("/friend-requests/{id}/reject")
  Object reject(HttpServletRequest r, @PathVariable long id) {
    service.decide(me(r), id, false);
    return Map.of();
  }

  @GetMapping("/conversations")
  Object conversations(HttpServletRequest r) {
    return items(service.conversations(me(r)));
  }

  @GetMapping("/messages/{peerId}")
  Object messages(
      HttpServletRequest r,
      @PathVariable long peerId,
      @RequestParam(defaultValue = "0") long after_id,
      @RequestParam(defaultValue = "100") int limit) {
    long id = me(r);
    page(after_id, limit, 100);
    return service.messages(id, peerId, after_id, limit);
  }

  @PostMapping("/messages/{peerId}")
  Object send(HttpServletRequest r, @PathVariable long peerId, @RequestBody Map<String, Object> b) {
    long id = me(r);
    fields(b, "client_id", "body");
    String body = string(b, "body", 1, 2000, false);
    if (body.isBlank()) throw new ApiError(400, "消息不能为空");
    return service.send(id, peerId, string(b, "client_id", 1, 80, true), body);
  }

  @PostMapping("/messages/{peerId}/read")
  Object read(HttpServletRequest r, @PathVariable long peerId, @RequestBody Map<String, Object> b) {
    long id = me(r);
    fields(b, "through_id");
    service.read(id, peerId, number(b, "through_id"));
    return Map.of();
  }

  @PostMapping("/media")
  Object upload(HttpServletRequest r, @RequestPart MultipartFile file) {
    long id = me(r);
    return Map.of("id", service.upload(id, Images.sanitize(file)));
  }

  @GetMapping("/media/{id}")
  ResponseEntity<byte[]> media(HttpServletRequest r, @PathVariable String id) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.media(me(r), id));
  }

  @PostMapping("/posts")
  Object post(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    long id = me(r);
    fields(b, "body", "media_id");
    String body = string(b, "body", 0, 2000, true);
    String media = b.get("media_id") == null ? null : string(b, "media_id", 1, 36, true);
    if (body.isEmpty() && media == null) throw new ApiError(400, "动态需要文字或图片");
    return service.createPost(id, body, media);
  }

  @GetMapping("/posts")
  Object posts(
      HttpServletRequest r,
      @RequestParam(defaultValue = "9223372036854775807") long before_id,
      @RequestParam(defaultValue = "20") int limit) {
    long id = me(r);
    page(before_id, limit, 100);
    return service.posts(id, before_id, limit);
  }

  @DeleteMapping("/posts/{id}")
  Object delete(HttpServletRequest r, @PathVariable long id) {
    service.delete(me(r), id);
    return Map.of();
  }

  @PutMapping("/posts/{id}/like")
  Object like(HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> b) {
    long me = me(r);
    fields(b, "liked");
    if (!(b.get("liked") instanceof Boolean)) throw new ApiError(400, "liked 必须为布尔值");
    return service.like(me, id, (Boolean) b.get("liked"));
  }

  @PostMapping("/posts/{id}/comments")
  Object comment(HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> b) {
    long me = me(r);
    fields(b, "body");
    return service.comment(me, id, string(b, "body", 1, 500, true));
  }

  @PostMapping("/ws-ticket")
  Object ticket(HttpServletRequest r) {
    long id = me(r);
    return Map.of("ticket", socket.ticket(id, auth.bearer(r.getHeader("Authorization"))));
  }
}
