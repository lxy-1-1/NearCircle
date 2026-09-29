package io.github.lxy.nearcircle.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {ChatMessage.class, SyncCursor.class},
    version = 1,
    exportSchema = true)
public abstract class ChatDatabase extends RoomDatabase {
  public abstract MessageDao messages();
}
