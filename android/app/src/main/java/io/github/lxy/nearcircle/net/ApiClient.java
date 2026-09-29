package io.github.lxy.nearcircle.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class ApiClient {
  public static final OkHttpClient HTTP =
      new OkHttpClient.Builder()
          .connectTimeout(10, TimeUnit.SECONDS)
          .readTimeout(20, TimeUnit.SECONDS)
          .callTimeout(30, TimeUnit.SECONDS)
          .build();
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private final String base, token;

  public ApiClient(String base, String token) {
    this.base = normalizeBase(base);
    this.token = token;
  }

  public static String normalizeBase(String value) {
    try {
      URI uri = new URI(value.trim());
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
        throw new IllegalArgumentException();
      return value.trim().replaceAll("/+$", "");
    } catch (Exception e) {
      throw new IllegalArgumentException("请输入完整的 http(s) 服务地址，不包含账号、路径或查询参数");
    }
  }

  public JsonObject get(String path) throws IOException {
    return json("GET", path, null);
  }

  public JsonObject post(String path, JsonObject body) throws IOException {
    return json("POST", path, body);
  }

  public JsonObject put(String path, JsonObject body) throws IOException {
    return json("PUT", path, body);
  }

  public JsonObject patch(String path, JsonObject body) throws IOException {
    return json("PATCH", path, body);
  }

  public JsonObject delete(String path) throws IOException {
    return json("DELETE", path, null);
  }

  private Request.Builder request(String path) {
    Request.Builder r =
        new Request.Builder().url(base + "/api" + path).header("Accept", "application/json");
    if (!token.isEmpty()) r.header("Authorization", "Bearer " + token);
    return r;
  }

  private JsonObject json(String method, String path, JsonObject body) throws IOException {
    RequestBody payload =
        ("GET".equals(method) || "DELETE".equals(method))
            ? null
            : RequestBody.create(body == null ? "{}" : body.toString(), JSON);
    try (Response response =
        HTTP.newCall(request(path).method(method, payload).build()).execute()) {
      return decode(response);
    }
  }

  private JsonObject decode(Response response) throws IOException {
    String text = response.body() == null ? "" : response.body().string();
    JsonObject object;
    try {
      object = text.isEmpty() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
    } catch (Exception malformed) {
      if (!response.isSuccessful())
        throw new ApiException(response.code(), "服务暂时不可用（" + response.code() + "）");
      throw new IOException("服务响应格式不正确");
    }
    if (!response.isSuccessful())
      throw new ApiException(
          response.code(),
          object.has("message")
              ? object.get("message").getAsString()
              : "请求失败（" + response.code() + "）");
    return object;
  }

  public JsonObject upload(byte[] bytes) throws IOException {
    RequestBody form =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "photo.jpg", RequestBody.create(bytes, MediaType.get("image/jpeg")))
            .build();
    try (Response response = HTTP.newCall(request("/media").post(form).build()).execute()) {
      return decode(response);
    }
  }

  public byte[] image(String id) throws IOException {
    try (Response response = HTTP.newCall(request("/media/" + id).build()).execute()) {
      if (!response.isSuccessful()) throw new ApiException(response.code(), "图片暂不可用");
      final int limit = 6 * 1024 * 1024;
      if (response.body() == null || response.body().contentLength() > limit)
        throw new IOException("图片过大");
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      try (java.io.InputStream input = response.body().byteStream()) {
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (output.size() + count > limit) throw new IOException("图片过大");
          output.write(buffer, 0, count);
        }
      }
      return output.toByteArray();
    }
  }

  public String socketUrl(String ticket) {
    return HttpUrl.get(base + "/ws")
        .newBuilder()
        .addQueryParameter("ticket", ticket)
        .build()
        .toString();
  }

  public static final class ApiException extends IOException {
    public final int code;

    public ApiException(int code, String message) {
      super(message);
      this.code = code;
    }

    public boolean retryable() {
      return code == 408 || code == 429 || code >= 500;
    }
  }
}
