package io.github.lxy.nearcircle.data;

import com.google.gson.JsonObject;

/** Immutable credentials: a request always belongs to the account that created it. */
public final class Session {
  public final String baseUrl, token, username, displayName, bio;
  public final long userId;

  public Session(String baseUrl, String token, JsonObject user) {
    this.baseUrl = baseUrl;
    this.token = token;
    this.userId = user.get("id").getAsLong();
    username = text(user, "username");
    displayName = text(user, "display_name");
    bio = text(user, "bio");
  }

  public String owner() {
    return baseUrl + "|" + userId;
  }

  public static String text(JsonObject o, String key) {
    return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
  }
}
