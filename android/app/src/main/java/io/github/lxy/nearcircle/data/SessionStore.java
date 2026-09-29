package io.github.lxy.nearcircle.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** No plaintext auth token in SharedPreferences or backup. */
public final class SessionStore {
  private static final String KEY = "nearcircle.session.v1";
  private final SharedPreferences prefs;
  private Session current;

  public SessionStore(Context context) {
    prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE);
    try {
      String data = prefs.getString("sealed", null);
      if (data != null) {
        String[] parts = data.split(":");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        JsonObject value =
            JsonParser.parseString(
                    new String(
                        cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                        java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        current =
            new Session(
                value.get("base").getAsString(),
                value.get("token").getAsString(),
                value.getAsJsonObject("user"));
      }
    } catch (Exception invalid) {
      prefs.edit().remove("sealed").apply();
    }
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (!store.containsAlias(KEY)) {
      KeyGenerator generator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(
          new KeyGenParameterSpec.Builder(
                  KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      generator.generateKey();
    }
    return (SecretKey) store.getKey(KEY, null);
  }

  public synchronized Session get() {
    return current;
  }

  public synchronized boolean matches(Session s) {
    return s != null
        && current != null
        && current.owner().equals(s.owner())
        && current.token.equals(s.token);
  }

  public synchronized void save(String base, String token, JsonObject user) throws Exception {
    JsonObject value = new JsonObject();
    value.addProperty("base", base);
    value.addProperty("token", token);
    value.add("user", user);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String sealed =
        Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(
                cipher.doFinal(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    if (!prefs.edit().putString("sealed", sealed).putString("server", base).commit())
      throw new java.io.IOException("无法保存登录信息");
    current = new Session(base, token, user);
  }

  public synchronized void clear() {
    current = null;
    prefs.edit().remove("sealed").apply();
  }

  /** A delayed response must never clear credentials installed by a later login. */
  public synchronized boolean clearIfCurrent(Session expected) {
    if (!matches(expected)) return false;
    clear();
    return true;
  }

  public String lastServer() {
    return prefs.getString("server", "http://10.0.2.2:8080");
  }
}
