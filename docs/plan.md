# NearCircle Implementation Plan

> 执行使用 writing-plans / test-driven-development 工作流；独立服务端和环境准备并行，Android 主流程本会话实现，最后独立审查。

**Goal:** 可安装的 Java Android 熟人社交 App 与可本地启动的 Java 服务端，并上传用户私有 GitHub。
**Architecture:** Java Android UI + Room 本地消息/outbox + WorkManager + OkHttp REST/WebSocket；Spring Boot REST/WebSocket + 文件数据库。
**Spec:** [design.md](design.md)

## 任务与完成证据
- [ ] 服务端：server/ 下实现固定 API 契约和安全边界。先写集成测试，以未实现 endpoint 的失败为红灯，再实现；运行 `mvn test` 与 package。接口名和字段严格遵循 design.md。
- [ ] 构建环境：android/ Gradle wrapper，Java17，SDK35，Gradle8.11.1/AGP8.10；准备可重复构建和模拟器。如无可运行设备，记录限制，不伪造截图。
- [ ] Android 数据层：SessionStore、ApiClient、Room ChatMessage/Dao、MessageRepository、SendWorker；先覆盖消息归并、排序、客户端 ID 持久化、重试分类的 JVM 测试。每条消息 owner_id 分区，失效鉴权不得作为瞬时网络失败无限重试。
- [ ] Android 原生界面：登录/注册/服务地址，四页导航、联系人申请、聊天气泡、待发送状态、朋友圈发布与图片、资料编辑。所有网络在 executor/worker，主线程渲染 RecyclerView；状态错误可恢复。
- [ ] 联调：启动 demo server，多账号真实 HTTP；模拟器安装、登录、发送、断网、恢复、旋转、发布；检查 logcat、截图、lint。缺陷写回归后修复。
- [ ] 交付：中文 README、启动脚本、API/架构/验证/面试文档、CI；独立审查后修复重要发现；APK 本地输出与 GitHub release（如可用），用户唯一 Git 作者，push 并核对远端 SHA。

## Review focus
- 重试消息不能串账号、重发重复或被旧 worker 覆盖。
- API 对查询、媒体、评论、WS 均执行权限边界。
- 会话翻页必须能补齐超过一页的消息。
- UI 重建和切换账号不显示上个账号数据。
- 网络错误与真实空列表区分；注册校验与密码不出现在日志。
