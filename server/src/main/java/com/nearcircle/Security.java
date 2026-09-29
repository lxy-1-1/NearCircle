package com.nearcircle;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Security {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int ITERATIONS = 210_000;
  private final JdbcTemplate db;

  public Security(JdbcTemplate db) {
    this.db = db;
  }

  static String random() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static String password(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return ITERATIONS
        + ":"
        + Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
  }

  static boolean matches(String password, String encoded) {
    String[] parts = encoded.split(":");
    return MessageDigest.isEqual(
        derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0])),
        Base64.getDecoder().decode(parts[2]));
  }

  private static byte[] derive(String password, byte[] salt, int iterations) {
    var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }

  String token(long id) {
    String token = random();
    db.update(
        "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",
        hash(token),
        id,
        System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000);
    return token;
  }

  String bearer(String header) {
    if (header == null || !header.startsWith("Bearer ") || header.length() > 256)
      throw new ApiError(401, "请先登录");
    return hash(header.substring(7));
  }

  long require(String header) {
    return userForHash(bearer(header));
  }

  long userForHash(String hash) {
    var ids =
        db.query(
            "SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>?",
            (rs, n) -> rs.getLong(1),
            hash,
            System.currentTimeMillis());
    if (ids.isEmpty()) throw new ApiError(401, "登录已过期，请重新登录");
    return ids.get(0);
  }

  void logout(String header) {
    db.update("DELETE FROM sessions WHERE token_hash=?", bearer(header));
  }
}
