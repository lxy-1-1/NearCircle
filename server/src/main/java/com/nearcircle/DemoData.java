package com.nearcircle;

import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
class DemoData implements CommandLineRunner {
  private final SocialService service;
  private final JdbcTemplate db;

  DemoData(SocialService service, JdbcTemplate db) {
    this.service = service;
    this.db = db;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (db.queryForObject("SELECT COUNT(*) FROM users", Integer.class) > 0) return;
    long lin = id(service.register("lin", "NearCircle2026!", "林间"));
    long zhou = id(service.register("zhou", "NearCircle2026!", "周予"));
    long xu = id(service.register("xuzhi", "NearCircle2026!", "许知"));
    connect(lin, zhou);
    connect(lin, xu);
    service.profile(lin, "林间", "把平凡日子过得闪闪发光。");
    service.profile(zhou, "周予", "散步、咖啡，和好朋友。");
    service.profile(xu, "许知", "记录一些小美好。");
    service.send(zhou, lin, "demo-zhou-1", "周末一起去湖边走走吗？");
    service.send(lin, zhou, "demo-lin-1", "好呀！我带相机，下午三点见。");
    service.send(zhou, lin, "demo-zhou-2", "说定了，记得给我拍好看一点 ☀");
    service.send(xu, lin, "demo-xu-1", "分享一首新歌，下次见面放给你听。");
    long p =
        ((Number) service.createPost(zhou, "今天的快乐：一杯拿铁，一段没有目的地的散步。", null).get("id")).longValue();
    service.like(lin, p, true);
    service.comment(lin, p, "生活的小美好 ☕");
    service.createPost(xu, "把手机放进口袋，抬头看看晚霞。", null);
    service.createPost(lin, "熟悉的人，值得认真分享的日常。你好，近友。", null);
  }

  long id(Map<String, Object> auth) {
    return ((Number) ((Map<?, ?>) auth.get("user")).get("id")).longValue();
  }

  void connect(long a, long b) {
    service.request(a, b);
    service.decide(b, ((Number) service.requests(b).get(0).get("id")).longValue(), true);
  }
}
