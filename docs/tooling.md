# Android 构建与模拟器

工程锁定 Java 17、Android Gradle Plugin 8.10.0、Gradle 8.11.1、compileSdk/targetSdk 35、minSdk 26。Gradle wrapper 已随源码提交。首次构建需要网络下载 Google Maven / Maven Central 依赖，缓存位于仓库 `work/gradle-home`，该目录不提交。

Android Studio 安装 SDK Platform 35 和 Build Tools 35.0.0 后，设置 `JAVA_HOME` 指向 JDK 17，设置 `ANDROID_HOME` 指向 SDK。Windows 在仓库根目录执行：

```powershell
./scripts/android-build.ps1
```

脚本默认执行 JVM 单元测试、Debug APK 编译和 Android lint。只运行某些任务：

```powershell
./scripts/android-build.ps1 -Tasks testDebugUnitTest,assembleDebug
```

其他平台可直接进入 `android` 并执行 `./gradlew testDebugUnitTest assembleDebug lintDebug`。Debug APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。本项目使用 Java Room annotationProcessor，数据库结构导出到 `android/app/schemas`。

本机 SDK 和 JDK 的路径仅存放在环境变量或忽略的 `local.properties` 中；源码不会要求其他开发者具备 E 盘缓存。没有 release 签名密钥，Debug APK 只用于开发与演示。

## 官方工具依据

- [AGP 8.10 兼容性](https://developer.android.com/build/releases/agp-8-10-0-release-notes)：Gradle 8.11.1、JDK 17。
- [Android Emulator 命令行](https://developer.android.com/studio/run/emulator-commandline)。
- [Android SDK 工具](https://developer.android.com/studio#command-line-tools-only)。

## API 35 模拟器隔离

本机验证使用 Google 官方 AOSP API 35 x86_64 系统镜像，镜像、AVD 数据和日志全部保存在忽略的 `work` 目录。模拟器使用现有 SDK 的 emulator/platform-tools，不改动 SDK 安装。无窗口启动需要系统允许硬件加速驱动访问；受限沙箱不能访问 AEHD，需授权在沙箱外启动。

下载 [API 35 x86_64 revision 2](https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip)，按照 [官方仓库元数据](https://dl.google.com/android/repository/sys-img/android/sys-img2-3.xml) 核对 SHA-1 `2d857d170c0d1b827149565da34b3383e5306f7f`，解压到 `work/android-sdk/system-images/android-35/default`，使 `x86_64/system.img` 存在。运行 `./scripts/start-emulator.ps1`，再用 SDK platform-tools 下的 adb 查询 `adb -s emulator-5554 shell getprop sys.boot_completed`，输出 `1` 表示启动完成。模拟器采用 1080×1920、420 dpi、2 GB 内存；日志保存在 `work/emulator.stdout.log` 和 `work/emulator.stderr.log`。

服务端在宿主机监听 8080 时，模拟器使用 `http://10.0.2.2:8080`。真机应改成同一局域网内电脑的地址。HTTP 仅用于本地演示；生产部署应配置 HTTPS。
