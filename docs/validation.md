# 验证记录

本机验证日期：2026-09-29。Windows、JDK 17.0.17、Gradle 8.11.1、SDK/模拟器 API 35；模拟器分辨率 1080×1920，420 dpi。测试使用本机演示服务，没有使用真实用户资料。

## 可重复执行

```powershell
# 服务端，11 项测试
cd server
mvn test package
cd ..

# Android JVM、打包、静态检查
./scripts/android-build.ps1 -Tasks testDebugUnitTest,assembleDebug,lintDebug

# 需要已连接的测试设备；6 项仪器化测试
./scripts/android-build.ps1 -Tasks connectedDebugAndroidTest

# 真实 App 操作；需要 demo 服务与模拟器，只清理模拟器中的近友 App 数据
python scripts/e2e_smoke.py --adb "你的 SDK/platform-tools/adb.exe" --reset-app
```

## 验证层次

| 层次 | 数量/内容 | 证据位置 |
| --- | --- | --- |
| Spring Boot 集成与回归 | 11 项；身份验证、好友越权、消息幂等/分页、媒体隐私、demo、提交顺序、WebSocket | `server/target/surefire-reports`（构建生成） |
| Android JVM | 6 项；真实 MockWebServer HTTP、账号绑定头、错误分类、响应校验、媒体流上限 | `android/app/build/reports/tests/testDebugUnitTest` |
| Android 仪器化 | 6 项；真实 Room、Keystore、Worker、ViewModel 与 Activity | `android/app/build/reports/androidTests/connected` |
| 静态检查与 APK | Debug 构建、lint 0 errors；依赖更新提醒与中文界面国际化等 warnings 保留 | `android/app/build/reports/lint-results-debug.html` |
| 真实操作 | 由 UI 冒烟脚本输出明细；屏幕为设备实际截图 | [e2e-result.json](e2e-result.json)、[screenshots/](screenshots/) |

## 曾失败并修复的回归

- 双向发送可能先提交较大 ID，导致游标漏掉较小 ID。通过锁定两端并保持固定加锁顺序修复；测试持有第一笔事务后检查第二笔不能提前提交。
- 相同已读游标不断发布刷新，可能形成客户端刷新/已读回路。更新行数为零时不广播。
- 详细 JPEG 重编码 PNG 后超过 6 MiB，手机无法读取。输入 5 MiB 以内的测试 JPEG 曾输出 7,491,750 字节；修复后存储结果最多 5 MiB，Android 同时对分块流限制读取上限。
- 旧 Worker 的 401 把重新登录账号的待发记录改成失败。使用原 Session 检查与原子状态更新。
- 图片处理中移除图片，发布按钮一直不可用。清除 PHOTO 状态并给异步返回增加代际检查。
- 朋友圈加载中收到通知但不再更新。合并刷新信号，完成后继续补拉。
- 发帖已保存但返回列表看不到新条目。发布成功回传 Activity Result，拉取后滚动到最新动态。

## 不应从这些结果推出的结论

没有测量百万消息容量、吞吐量、真实手机后台保活、耗电、多品牌兼容、弱网长期运行、多实例一致性或应用商店合规。没有使用这些测试数量证明“生产级”或保证面试结果。CI 配置随仓库提供，远端执行状态以 GitHub Actions 为准。
