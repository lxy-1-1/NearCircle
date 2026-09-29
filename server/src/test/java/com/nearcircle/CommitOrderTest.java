package com.nearcircle;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ordering;DB_CLOSE_DELAY=-1")
@RecordApplicationEvents
class CommitOrderTest {
  @Autowired SocialService service;
  @Autowired PlatformTransactionManager manager;
  @Autowired ApplicationEvents events;

  long account(String name) {
    return ((Number)
            ((Map<?, ?>) service.register(name, "Password123!", name).get("user")).get("id"))
        .longValue();
  }

  void friends(long a, long b) {
    service.request(a, b);
    service.decide(b, ((Number) service.requests(b).get(0).get("id")).longValue(), true);
  }

  @Test
  void oppositeDirectionCannotCommitPastAnUncommittedEarlierMessage() throws Exception {
    long a = account("order_a"), b = account("order_b");
    friends(a, b);
    var inserted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first =
          pool.submit(
              () ->
                  new TransactionTemplate(manager)
                      .execute(
                          status -> {
                            var m = service.send(a, b, "first", "first");
                            inserted.countDown();
                            try {
                              if (!release.await(5, TimeUnit.SECONDS))
                                throw new IllegalStateException("test release timed out");
                            } catch (InterruptedException e) {
                              throw new RuntimeException(e);
                            }
                            return m;
                          }));
      assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
      var second =
          pool.submit(
              () -> {
                started.countDown();
                return service.send(b, a, "second", "second");
              });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.get(350, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      release.countDown();
      long firstId = ((Number) first.get(5, TimeUnit.SECONDS).get("id")).longValue();
      long secondId = ((Number) second.get(5, TimeUnit.SECONDS).get("id")).longValue();
      assertThat(secondId).isGreaterThan(firstId);
      assertThat((java.util.List<?>) service.messages(a, b, 0, 100).get("items")).hasSize(2);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void readingTheSameCursorTwiceDoesNotTriggerRefreshFeedbackLoop() {
    long a = account("read_a"), b = account("read_b");
    friends(a, b);
    long id = ((Number) service.send(a, b, "read-once", "hello").get("id")).longValue();
    service.read(b, a, id);
    long count = events.stream(SocialService.Refresh.class).count();
    service.read(b, a, id);
    assertThat(events.stream(SocialService.Refresh.class).count()).isEqualTo(count);
  }
}
