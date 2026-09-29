package io.github.lxy.nearcircle.net;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiClientTest {
  private MockWebServer server;

  @Before
  public void before() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @After
  public void after() throws Exception {
    server.shutdown();
  }

  @Test
  public void sendsStableClientIdAndAccountBoundAuthorization() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"id\":42}"));
    JsonObject body = new JsonObject();
    body.addProperty("client_id", "retry-same-id");
    body.addProperty("body", "你好");
    ApiClient api = new ApiClient(server.url("/").toString(), "account-a-token");
    assertEquals(42, api.post("/messages/2", body).get("id").getAsLong());
    RecordedRequest request = server.takeRequest();
    assertEquals("/api/messages/2", request.getPath());
    assertEquals("Bearer account-a-token", request.getHeader("Authorization"));
    assertTrue(request.getBody().readUtf8().contains("retry-same-id"));
  }

  @Test
  public void unauthorizedDoesNotBecomeAnEmptySuccessfulResponse() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"登录已失效\"}"));
    try {
      new ApiClient(server.url("/").toString(), "old").get("/friends");
      fail("Expected rejected session");
    } catch (ApiClient.ApiException e) {
      assertEquals(401, e.code);
      assertFalse(e.retryable());
      assertEquals("登录已失效", e.getMessage());
    }
  }

  @Test
  public void temporaryServerFailureCanRetryButConflictCannot() throws Exception {
    for (int code : new int[] {503, 409, 429}) {
      server.enqueue(
          new MockResponse().setResponseCode(code).setBody("{\"message\":\"request rejected\"}"));
      try {
        new ApiClient(server.url("/").toString(), "t").get("/friends");
        fail();
      } catch (ApiClient.ApiException e) {
        assertEquals(code != 409, e.retryable());
      }
    }
  }

  @Test
  public void rejectsCredentialsAndNonHttpSchemesInServerAddress() {
    for (String address :
        new String[] {
          "file:///tmp/x", "http://user:password@localhost:8080", "https://example.com?token=secret"
        }) {
      try {
        ApiClient.normalizeBase(address);
        fail(address);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void malformedSuccessIsNotAcceptedAsAcknowledgement() throws Exception {
    server.enqueue(new MockResponse().setBody("<html>proxy</html>"));
    try {
      new ApiClient(server.url("/").toString(), "t").get("/friends");
      fail();
    } catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("响应"));
    }
  }

  @Test
  public void rejectsOversizedChunkedMediaWithoutContentLength() throws Exception {
    byte[] huge = new byte[6 * 1024 * 1024 + 1];
    server.enqueue(new MockResponse().setChunkedBody(new okio.Buffer().write(huge), 8192));
    try {
      new ApiClient(server.url("/").toString(), "t").image("id");
      fail("oversized streamed body");
    } catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("过大"));
    }
  }
}
