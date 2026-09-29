package com.nearcircle;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RefreshSocket extends TextWebSocketHandler implements HandshakeInterceptor {
  record Ticket(long user, String hash, long expires) {}

  private final Security auth;
  private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

  public RefreshSocket(Security auth) {
    this.auth = auth;
  }

  public synchronized String ticket(long user, String hash) {
    long now = System.currentTimeMillis();
    tickets.entrySet().removeIf(e -> e.getValue().expires < now || e.getValue().hash.equals(hash));
    if (tickets.size() >= 10_000) throw new ApiError(429, "连接繁忙，请稍后重试");
    String token = Security.random();
    tickets.put(Security.hash(token), new Ticket(user, hash, now + 30_000));
    return token;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attrs) {
    String token =
        UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
    Ticket ticket = token == null ? null : tickets.remove(Security.hash(token));
    if (ticket == null || ticket.expires < System.currentTimeMillis()) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    try {
      if (auth.userForHash(ticket.hash) != ticket.user) throw new ApiError(401, "失效");
    } catch (ApiError e) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    attrs.put("user", ticket.user);
    attrs.put("hash", ticket.hash);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    try {
      auth.userForHash((String) session.getAttributes().get("hash"));
      sessions.add(session);
    } catch (ApiError e) {
      session.close(CloseStatus.POLICY_VIOLATION);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    close(session);
  }

  void close(WebSocketSession session) {
    sessions.remove(session);
    try {
      session.close(CloseStatus.NORMAL);
    } catch (IOException ignored) {
    }
  }

  public void revoke(String hash) {
    tickets.entrySet().removeIf(e -> e.getValue().hash.equals(hash));
    for (var s : sessions) if (hash.equals(s.getAttributes().get("hash"))) close(s);
  }

  @TransactionalEventListener
  public void refresh(SocialService.Refresh event) {
    for (var s : sessions) {
      if (!event.users().contains((Long) s.getAttributes().get("user"))) continue;
      try {
        auth.userForHash((String) s.getAttributes().get("hash"));
        synchronized (s) {
          if (s.isOpen()) s.sendMessage(new TextMessage("{\"type\":\"refresh\"}"));
        }
      } catch (IOException | ApiError e) {
        close(s);
      }
    }
  }
}

@Configuration
@EnableWebSocket
class SocketConfiguration implements WebSocketConfigurer {
  private final RefreshSocket socket;

  SocketConfiguration(RefreshSocket socket) {
    this.socket = socket;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(socket, "/ws").addInterceptors(socket);
  }
}
