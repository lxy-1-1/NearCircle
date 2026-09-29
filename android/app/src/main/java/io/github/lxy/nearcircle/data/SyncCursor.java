package io.github.lxy.nearcircle.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(
    tableName = "sync_cursors",
    primaryKeys = {"owner", "peerId"})
public class SyncCursor {
  @NonNull public String owner = "";
  public long peerId, lastId;
}
