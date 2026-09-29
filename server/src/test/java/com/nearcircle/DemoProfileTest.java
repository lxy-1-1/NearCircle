package com.nearcircle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class DemoProfileTest {
  @Autowired SocialService service;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @Autowired DemoData demo;

  @Test
  void explicitDemoProvidesThreeAccountsAndRelationshipsWithoutResetOnRerun() throws Exception {
    var login = service.login("lin", "NearCircle2026!");
    long id = ((Number) ((java.util.Map<?, ?>) login.get("user")).get("id")).longValue();
    assertThat(service.friends(id)).hasSize(2);
    assertThat(service.conversations(id)).hasSize(2);
    assertThat((java.util.List<?>) service.posts(id, Long.MAX_VALUE, 20).get("items")).hasSize(3);
    assertThatThrownBy(() -> service.login("lin", "wrongpassword")).isInstanceOf(ApiError.class);
    demo.run();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(3);
  }
}
