# NearCircle 服务端

Java 17、Spring Boot 3.5.16、Spring JDBC、H2 文件数据库。API 契约见 `../docs/design.md`。WebSocket 只通知刷新，聊天完整记录通过 HTTP 游标补齐。

## 运行

需要 JDK 17 和 Maven 3.9+，在本目录执行：

```powershell
mvn test
mvn package
java -jar target/nearcircle-server-1.0.0.jar
```

默认端口 `8080`，健康检查 `GET http://localhost:8080/api/health`。默认运行不会生成测试账号。首次启动会创建 `data/nearcircle.mv.db`，消息、资料、动态和图片都在数据库中持久化。停止 Java 进程后备份整个 `data` 目录；不要让两个服务进程同时打开同一文件数据库。

显式演示模式：

```powershell
java -jar target/nearcircle-server-1.0.0.jar --spring.profiles.active=demo
```

仅在数据库没有任何用户时，demo 模式创建 `lin`、`zhou`、`xuzhi`，共同密码 `NearCircle2026!`。林间与另外两人互为好友，包含聊天和动态。已有数据库不会重置。需要独立演示数据库可追加 `--spring.datasource.url=jdbc:h2:file:./data/demo`。

Android 模拟器的服务地址填 `http://10.0.2.2:8080`，同局域网真机填电脑的局域网地址与端口。公网部署须在服务前配置 HTTPS；本工程提供的是单机作品服务，不包含多实例协调、验证码、反滥用限流或运维系统。

配置项：`--server.port=8081` 修改端口；环境变量 `NEARCIRCLE_DB_URL`、`NEARCIRCLE_DB_PASSWORD` 修改数据库连接。不要把数据库、口令或 token 提交到 Git。

## 权限和一致性

- 密码为 PBKDF2-HMAC-SHA256、随机 16 字节 salt、210,000 次迭代。会话使用随机 32 字节 opaque token，数据库只存 SHA-256 摘要，30 天过期；退出立即删除会话并关闭关联 WebSocket。
- 写操作由服务端登录态确定用户，不接受 `sender_id`、`owner_id` 等越权字段；用户名大小写统一。
- 好友申请只有收件人可处理；好友关系双向写入同一事务。关系检查用于消息、动态、点赞、评论和媒体。
- 消息以 `(sender_id,client_id)` 唯一约束并串行化同一发送者的写操作；相同收件人、相同原文的重试返回原记录，改内容或收件人返回 409。
- 消息和动态游标均为 exclusive。消息按 ID 正序、`after_id` 补齐；动态按 ID 倒序、`before_id` 翻页。已读 SQL 同时限制发送者与收件人，不会清除其他会话未读。
- 图片读取前检查真实格式与宽高，限制上传 5 MiB、最长边 4096、总像素 16,000,000。支持 PNG/JPEG/WebP 解码，统一重编码 PNG 去除原始元数据。上传的图片仅本人可读，好友必须等本人发布引用它的动态后才可读；删除所有引用后好友权限消失。媒体响应 `Cache-Control: no-store`。
- WebSocket ticket 只存摘要，30 秒、单次原子消费，每个会话最多一个待消费 ticket。握手核对会话有效性。事务提交后才发 `{type:"refresh"}`；前端仍须通过 HTTP 补齐。服务重启会断开 socket，数据库会话继续有效。

## 验证

`mvn test` 使用独立内存 H2，不操作实际数据库。集成测试覆盖注册校验、注销失效、好友申请越权、双向关系、非好友聊天拒绝、并发幂等消息、冲突返回、102 条消息分页、会话范围已读、动态分页与所有权、媒体隐私与删除后的权限、伪图片/超宽图片拒绝、实际 HTTP/WebSocket 握手、ticket 重用拒绝、提交后刷新、注销关闭 socket。

Spring Boot 版本来源：[官方 3.5.16 发布说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)。这是 3.5 分支最终 OSS 版本；正式持续维护的服务应规划升级到受支持分支。
