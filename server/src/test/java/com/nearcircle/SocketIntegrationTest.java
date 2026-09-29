package com.nearcircle;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.url=jdbc:h2:mem:socket;DB_CLOSE_DELAY=-1"})
class SocketIntegrationTest {
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  final HttpClient client = HttpClient.newHttpClient();

  JsonNode post(String path, String token, Object body) throws Exception {
    var req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
            .header("Content-Type", "application/json");
    if (token != null) req.header("Authorization", "Bearer " + token);
    var response =
        client.send(
            req.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }

  static class Listener implements WebSocket.Listener {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();

    public void onOpen(WebSocket ws) {
      ws.request(1);
    }

    public CompletionStage<?> onText(WebSocket ws, CharSequence message, boolean last) {
      messages.add(message.toString());
      ws.request(1);
      return null;
    }

    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
      closed.complete(status);
      return null;
    }
  }

  CompletableFuture<WebSocket> connect(String ticket, Listener listener) {
    return client
        .newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + port + "/ws?ticket=" + ticket), listener);
  }

  @Test
  void ticketIsSingleUseAndRefreshFollowsCommittedWriteThenLogoutClosesSocket() throws Exception {
    String token =
        post(
                "/auth/register",
                null,
                Map.of(
                    "username",
                    "socket_user",
                    "password",
                    "Password123!",
                    "display_name",
                    "Socket"))
            .get("token")
            .asText();
    String ticket = post("/ws-ticket", token, Map.of()).get("ticket").asText();
    var listener = new Listener();
    var socket = connect(ticket, listener).get(5, TimeUnit.SECONDS);
    assertThatThrownBy(() -> connect(ticket, new Listener()).get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
    post("/posts", token, Map.of("body", "commit before refresh"));
    assertThat(listener.messages.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"refresh\"}");
    String revoked = post("/ws-ticket", token, Map.of()).get("ticket").asText();
    post("/auth/logout", token, Map.of());
    assertThat(listener.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1000);
    assertThatThrownBy(() -> connect(revoked, new Listener()).get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
    socket.abort();
  }
}
