package com.nearcircle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1",
      "spring.sql.init.mode=always"
    })
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  record Account(String token, long id) {}

  JsonNode call(String method, String path, String token, Object body, int expected)
      throws Exception {
    var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api" + path);
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
    String value = result.getResponse().getContentAsString();
    return value.isEmpty() ? json.nullNode() : json.readTree(value);
  }

  Account account() throws Exception {
    var r =
        call(
            "POST",
            "/auth/register",
            null,
            Map.of(
                "username",
                "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 15),
                "password",
                "SecurePassword42!",
                "display_name",
                "测试用户"),
            200);
    return new Account(r.get("token").asText(), r.at("/user/id").asLong());
  }

  long request(Account a, Account b) throws Exception {
    call("POST", "/friend-requests", a.token, Map.of("user_id", b.id), 200);
    return call("GET", "/friend-requests", b.token, null, 200).at("/items/0/id").asLong();
  }

  void friends(Account a, Account b) throws Exception {
    call("POST", "/friend-requests/" + request(a, b) + "/accept", b.token, Map.of(), 200);
  }

  @Test
  void authenticationValidationAndLogout() throws Exception {
    call("GET", "/me", null, null, 401);
    call(
        "POST", "/auth/login", null, Map.of("username", "lin", "password", "NearCircle2026!"), 401);
    call(
        "POST",
        "/auth/register",
        null,
        Map.of("username", "bad", "password", "x", "display_name", "X"),
        400);
    var a = account();
    assertThat(call("GET", "/me", a.token, null, 200).get("id").asLong()).isEqualTo(a.id);
    call("PATCH", "/me", a.token, Map.of("display_name", "新名字", "bio", "你好"), 200);
    call("GET", "/users?q=x", a.token, null, 400);
    call("POST", "/auth/logout", a.token, Map.of(), 200);
    call("GET", "/me", a.token, null, 401);
  }

  @Test
  void friendshipRequiresRecipientAndProtectsChat() throws Exception {
    var a = account();
    var b = account();
    var c = account();
    call("GET", "/messages/" + b.id, a.token, null, 403);
    long id = request(a, b);
    call("POST", "/friend-requests/" + id + "/accept", a.token, Map.of(), 403);
    call("POST", "/friend-requests/" + id + "/reject", c.token, Map.of(), 403);
    call("POST", "/friend-requests/" + id + "/accept", b.token, Map.of(), 200);
    assertThat(call("GET", "/friends", a.token, null, 200).at("/items/0/id").asLong())
        .isEqualTo(b.id);
    assertThat(call("GET", "/friends", b.token, null, 200).at("/items/0/id").asLong())
        .isEqualTo(a.id);
  }

  @Test
  void messagesRetryConflictPagingAndReadAreScoped() throws Exception {
    var a = account();
    var b = account();
    var c = account();
    friends(a, b);
    friends(a, c);
    var body = Map.of("client_id", "message-1", "body", "你好");
    long first = call("POST", "/messages/" + b.id, a.token, body, 200).get("id").asLong();
    assertThat(call("POST", "/messages/" + b.id, a.token, body, 200).get("id").asLong())
        .isEqualTo(first);
    call(
        "POST",
        "/messages/" + b.id,
        a.token,
        Map.of("client_id", "message-1", "body", "different"),
        409);
    call("POST", "/messages/" + c.id, a.token, body, 409);
    long second =
        call(
                "POST",
                "/messages/" + b.id,
                a.token,
                Map.of("client_id", "message-2", "body", "再次"),
                200)
            .get("id")
            .asLong();
    var page = call("GET", "/messages/" + a.id + "?limit=1", b.token, null, 200);
    assertThat(page.get("has_more").asBoolean()).isTrue();
    assertThat(page.at("/items/0/id").asLong()).isEqualTo(first);
    assertThat(
            call("GET", "/messages/" + a.id + "?after_id=" + first, b.token, null, 200)
                .at("/items/0/id")
                .asLong())
        .isEqualTo(second);
    call("GET", "/messages/" + a.id + "?limit=101", b.token, null, 400);
    call("POST", "/messages/" + a.id + "/read", b.token, Map.of("through_id", second), 200);
    assertThat(
            call("GET", "/conversations", b.token, null, 200).at("/items/0/unread_count").asInt())
        .isZero();
    call("POST", "/messages/" + b.id, a.token, Map.of("client_id", "invalid", "body", " "), 400);
  }

  @Test
  void postsMediaAndInteractionsRespectVisibilityAndOwnership() throws Exception {
    var a = account();
    var b = account();
    var c = account();
    friends(a, b);
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var file = new MockMultipartFile("file", "image.png", "image/png", bytes.toByteArray());
    var uploaded =
        mvc.perform(
                MockMvcRequestBuilders.multipart("/api/media")
                    .file(file)
                    .header("Authorization", "Bearer " + a.token))
            .andExpect(status().isOk())
            .andReturn();
    String media = json.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(
            MockMvcRequestBuilders.get("/api/media/" + media)
                .header("Authorization", "Bearer " + b.token))
        .andExpect(status().isForbidden());
    long post =
        call("POST", "/posts", a.token, Map.of("body", "测试动态", "media_id", media), 200)
            .get("id")
            .asLong();
    mvc.perform(
            MockMvcRequestBuilders.get("/api/media/" + media)
                .header("Authorization", "Bearer " + b.token))
        .andExpect(status().isOk());
    mvc.perform(
            MockMvcRequestBuilders.get("/api/media/" + media)
                .header("Authorization", "Bearer " + c.token))
        .andExpect(status().isForbidden());
    assertThat(call("GET", "/posts", c.token, null, 200).get("items").size()).isZero();
    call("PUT", "/posts/" + post + "/like", c.token, Map.of("liked", true), 403);
    call("POST", "/posts/" + post + "/comments", c.token, Map.of("body", "no"), 403);
    call("DELETE", "/posts/" + post, b.token, null, 403);
    call("POST", "/posts", b.token, Map.of("body", "偷图", "media_id", media), 403);
    call("PUT", "/posts/" + post + "/like", b.token, Map.of("liked", true), 200);
    assertThat(
            call("PUT", "/posts/" + post + "/like", b.token, Map.of("liked", true), 200)
                .get("like_count")
                .asInt())
        .isEqualTo(1);
    assertThat(
            call("POST", "/posts/" + post + "/comments", b.token, Map.of("body", "赞"), 200)
                .get("comments")
                .size())
        .isEqualTo(1);
    call("POST", "/posts", a.token, Map.of("body", "第二条"), 200);
    var page = call("GET", "/posts?limit=1", b.token, null, 200);
    assertThat(
            call("GET", "/posts?before_id=" + page.get("next_cursor").asLong(), b.token, null, 200)
                .at("/items/0/id")
                .asLong())
        .isEqualTo(post);
    call("DELETE", "/posts/" + post, a.token, null, 200);
    mvc.perform(
            MockMvcRequestBuilders.get("/api/media/" + media)
                .header("Authorization", "Bearer " + b.token))
        .andExpect(status().isForbidden());
  }

  @Test
  void malformedImagesUnknownFieldsAndCrossConversationReadsAreRejectedOrScoped() throws Exception {
    var a = account();
    var b = account();
    var c = account();
    friends(a, b);
    friends(a, c);
    call(
        "POST",
        "/messages/" + b.id,
        a.token,
        Map.of("client_id", "spoof", "body", "hello", "sender_id", c.id),
        400);
    long fromB =
        call("POST", "/messages/" + a.id, b.token, Map.of("client_id", "read-b", "body", "B"), 200)
            .get("id")
            .asLong();
    long fromC =
        call("POST", "/messages/" + a.id, c.token, Map.of("client_id", "read-c", "body", "C"), 200)
            .get("id")
            .asLong();
    call("POST", "/messages/" + b.id + "/read", a.token, Map.of("through_id", fromC), 200);
    var conversations = call("GET", "/conversations", a.token, null, 200).get("items");
    assertThat(conversations.get(0).at("/peer/id").asLong()).isEqualTo(c.id);
    assertThat(conversations.get(0).get("unread_count").asInt()).isEqualTo(1);
    assertThat(conversations.get(1).get("unread_count").asInt()).isZero();
    var fake = new MockMultipartFile("file", "bad.png", "image/png", "<html>bad</html>".getBytes());
    mvc.perform(
            MockMvcRequestBuilders.multipart("/api/media")
                .file(fake)
                .header("Authorization", "Bearer " + a.token))
        .andExpect(status().isBadRequest());
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var huge = new MockMultipartFile("file", "huge.png", "image/png", bytes.toByteArray());
    mvc.perform(
            MockMvcRequestBuilders.multipart("/api/media")
                .file(huge)
                .header("Authorization", "Bearer " + a.token))
        .andExpect(status().isBadRequest());
    call("POST", "/friend-requests", a.token, Map.of("user_id", a.id), 400);
    call("POST", "/posts", a.token, Map.of("body", " "), 400);
  }

  @Test
  void concurrentRetriesPersistExactlyOnceAndFullMessageHistoryCanBePaged() throws Exception {
    var a = account();
    var b = account();
    friends(a, b);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var tasks =
          java.util.List.<java.util.concurrent.Callable<Long>>of(
              () ->
                  call(
                          "POST",
                          "/messages/" + b.id,
                          a.token,
                          Map.of("client_id", "concurrent", "body", "once"),
                          200)
                      .get("id")
                      .asLong(),
              () ->
                  call(
                          "POST",
                          "/messages/" + b.id,
                          a.token,
                          Map.of("client_id", "concurrent", "body", "once"),
                          200)
                      .get("id")
                      .asLong());
      var results = executor.invokeAll(tasks);
      assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
    } finally {
      executor.shutdownNow();
    }
    for (int i = 0; i < 101; i++)
      call(
          "POST",
          "/messages/" + b.id,
          a.token,
          Map.of("client_id", "page-" + i, "body", "message " + i),
          200);
    var first = call("GET", "/messages/" + a.id, b.token, null, 200);
    assertThat(first.get("items").size()).isEqualTo(100);
    assertThat(first.get("has_more").asBoolean()).isTrue();
    var next =
        call(
            "GET",
            "/messages/" + a.id + "?after_id=" + first.at("/items/99/id").asLong(),
            b.token,
            null,
            200);
    assertThat(next.get("items").size()).isEqualTo(2);
    assertThat(next.get("has_more").asBoolean()).isFalse();
  }
}
