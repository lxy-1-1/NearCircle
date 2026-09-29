package io.github.lxy.nearcircle.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "messages",
    primaryKeys = {"owner", "localId"},
    indices = {@Index(value = {"owner", "peerId", "serverId"})})
public class ChatMessage {
  @NonNull public String owner = "";
  @NonNull public String localId = "";
  @NonNull public String clientId = "";
  public long peerId, serverId, senderId, createdAt;
  @NonNull public String body = "";
  @NonNull public String status = "QUEUED";
  @NonNull public String error = "";
}
