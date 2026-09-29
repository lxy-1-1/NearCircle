package io.github.lxy.nearcircle.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MessageDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void put(ChatMessage message);

  @Query(
      "SELECT * FROM messages WHERE owner=:owner AND peerId=:peer ORDER BY createdAt ASC, serverId"
          + " ASC")
  LiveData<List<ChatMessage>> observe(String owner, long peer);

  @Query(
      "SELECT * FROM messages WHERE owner=:owner AND status='QUEUED' ORDER BY createdAt ASC LIMIT"
          + " 1")
  ChatMessage next(String owner);

  @Query(
      "SELECT * FROM messages WHERE owner=:owner AND peerId=:peer ORDER BY createdAt ASC,serverId"
          + " ASC")
  List<ChatMessage> all(String owner, long peer);

  @Query("SELECT lastId FROM sync_cursors WHERE owner=:owner AND peerId=:peer")
  Long cursorValue(String owner, long peer);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void checkpoint(SyncCursor cursor);

  @Query(
      "UPDATE messages SET status=:status,error=:error WHERE owner=:owner AND localId=:localId AND"
          + " serverId=0")
  void state(String owner, String localId, String status, String error);

  @Query("DELETE FROM messages WHERE owner=:owner")
  void clearAccount(String owner);
}
