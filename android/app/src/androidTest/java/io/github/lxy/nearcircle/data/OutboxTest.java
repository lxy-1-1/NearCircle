package io.github.lxy.nearcircle.data;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OutboxTest {
  @Test
  public void ackDoesNotAdvanceCatchUpCursorAndCannotBeDowngradedByFailure() {
    Context context = ApplicationProvider.getApplicationContext();
    ChatDatabase db =
        Room.inMemoryDatabaseBuilder(context, ChatDatabase.class).allowMainThreadQueries().build();
    try {
      ChatMessage m = new ChatMessage();
      m.owner = "server|1";
      m.localId = "1:uuid";
      m.clientId = "uuid";
      m.senderId = 1;
      m.peerId = 2;
      m.body = "hello";
      m.createdAt = 1;
      db.messages().put(m);
      assertNotNull(db.messages().next(m.owner));
      m.serverId = 200;
      m.status = "SENT";
      db.messages().put(m);
      assertNull(
          "A sent ack must not skip earlier incoming messages",
          db.messages().cursorValue(m.owner, 2));
      db.messages().state(m.owner, m.localId, "FAILED", "late timeout");
      assertEquals("SENT", db.messages().all(m.owner, 2).get(0).status);
      assertNull(db.messages().next("server|other-user"));
      assertTrue(db.messages().all("server|other-user", 2).isEmpty());
    } finally {
      db.close();
    }
  }

  @Test
  public void sameReceiptReplacesQueuedRowInsteadOfDuplicatingBubble() {
    Context context = ApplicationProvider.getApplicationContext();
    ChatDatabase db =
        Room.inMemoryDatabaseBuilder(context, ChatDatabase.class).allowMainThreadQueries().build();
    try {
      ChatMessage m = new ChatMessage();
      m.owner = "s|1";
      m.localId = "1:id";
      m.clientId = "id";
      m.peerId = 2;
      db.messages().put(m);
      m.serverId = 9;
      m.status = "SENT";
      db.messages().put(m);
      db.messages().put(m);
      assertEquals(1, db.messages().all("s|1", 2).size());
      assertNull(db.messages().next("s|1"));
    } finally {
      db.close();
    }
  }
}
