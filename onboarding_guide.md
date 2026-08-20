# DAILY · 使用与构建向导（Onboarding Guide）

> 面向新用户与新电脑的完整入门向导：浏览器直接用 → 数据备份迁移 → 构建安卓桌面应用 → 常见问题。
> 项目总览见 [README.md](README.md)，技术细节见 [Technical-Documentation.md](Technical-Documentation.md)。

---

## 1. 浏览器快速使用

DAILY 是单文件应用，无需安装任何东西：

1. 打开 `DAILY.html`（双击即可，或拖进任意浏览器）。
2. 首次进入会自动填充一组示例数据，方便体验；不需要可到「数据管理 → 清空示例数据」。
3. 数据自动保存在浏览器本地（`localStorage`），关闭页面不丢失。

移动端浏览器同样可用；若需要桌面图标、每日通知、分享接收等能力，见第 3 节构建安卓应用。

## 2. 数据备份与迁移

所有数据仅存本机浏览器，换设备/换浏览器时用导出/导入迁移：

- **导出**：顶栏「数据管理」→ 选择 **JSON 备份 / CSV 报表 / Markdown 周报** 三种格式之一。
  - JSON：完整备份（任务、打卡、待办、设置），导入还原用这个。
  - CSV / Markdown：报表与周报，供查看与汇报。
  - 若下载被浏览器拦截，点「没收到？」会打开手动保存面板，可一键复制全文。
- **导入**：同面板选择 JSON 文件即可；导入前会做校验与净化，格式异常会明确提示原因。
- **清空**：可单独清空示例数据，或清空全部数据（需二次确认）。

> 建议定期导出 JSON 备份（应用内每累计 30 次操作也会温和提醒一次）。

## 3. 构建安卓桌面应用

### 3.1 前置环境（一次性安装）

| 组件 | 版本要求 | 说明 |
|---|---|---|
| Node.js + npm | ≥ 22 | 跑 Capacitor CLI |
| Android Studio | 2024.x 或更新 | 内含 Android SDK、Build-Tools、Platform-Tools |
| JDK | **17** | 构建 AGP 8.x 工程必需 |
| Android SDK | Platform 34（API 34） | minSdk 24 / compileSdk 34 |

安装步骤：

1. 从 https://developer.android.com/studio 下载并安装 Android Studio（Apple Silicon 选 Apple chip 版）。
2. 首次启动走 Setup Wizard，选 Standard 安装（会自动装 SDK 与自带 JDK）。
3. 在 **SDK Manager** 确认已安装：`Android 14.0 (API 34)`、`Build-Tools`、`Platform-Tools`、`Command-line Tools (latest)`。
4. **JDK 17** 两种获取方式任选：
   - Android Studio 自带的 JBR（在「Settings → Build Tools → Gradle → Gradle JDK」里选 17）；
   - 或独立安装（macOS 可用 `brew install openjdk@17`，Windows 可用 Adoptium Temurin 17）。
   - 注意：新版 Android Studio 自带的 JBR 可能是更高版本（如 21/25），若构建报版本不兼容，请改用独立 JDK 17。

### 3.2 配置环境变量

**macOS（zsh）**——编辑 `~/.zshrc` 追加：

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="<你的 JDK 17 路径>"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$JAVA_HOME/bin:$PATH"
```

`JAVA_HOME` 填实际 JDK 17 路径，例如：
- Android Studio 自带 JBR：`/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Homebrew：`$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home`

使生效：`source ~/.zshrc`（新开终端自动生效）。Windows 用户在「系统属性 → 环境变量」中设置同名变量。

**验证**（新开终端）：

```sh
java -version        # 应显示 17.x
adb version          # 应显示 Android Debug Bridge version x.x.x
echo $ANDROID_HOME   # 应输出 SDK 路径
```

### 3.3 构建与安装

**debug（开发/自测）**：

```sh
cd android-app
bash build-android.sh   # 一键：npm install → 同步 HTML → cap sync → gradlew assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk   # 侧载到手机
```

**release（正式签名版，长期分发/更新用）**：

```sh
bash build-android.sh release
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

release 需要先配置签名：生成 keystore（`keytool -genkey ...`），密码写入 `~/.gradle/gradle.properties`（`DAILY_*` 键，不进仓库），`android/app/build.gradle` 已预留 `signingConfigs.release` 读取。详见 `android-app/README.md` 第四节。⚠️ release 与 debug 签名不同，**不能覆盖升级**（需先卸载 debug，数据会清空，先导出备份）；此后统一用 release 包更新。

脚本说明：
- 每次修改 `DAILY.html` 后，只需重跑 `bash build-android.sh` 即可刷新 APK（会自动把最新网页打进去）。
- 首次运行会联网下载 Gradle 与安卓依赖，耗时几分钟，属正常。
- 真机要求：手机开启「开发者选项 → USB 调试」，连接后 `adb devices` 显示 `device` 状态。

### 3.4 原生能力

打包后自动激活（浏览器版无副作用）：
- **每日提醒**：设置 → 每日提醒，到达时间发本地通知，点击跳回应用。
- **导出选路径**：导出时弹出系统文件保存框（SAF），可自由选择保存位置。
- **分享接收**：从其它 App 把 JSON 备份「分享到 DAILY」可直接导入。
- 应用桌面图标为暖白网格底 + 渐变绿勾；如需修改，见 `android-app/README.md`。

## 4. 常见问题

- **`SDK location not found` / `ANDROID_HOME is not set`** → 环境变量未生效，确认已写入且重启终端。
- **`compileSdk 34 requires JDK 17`** → 当前 JDK 版本不对，按 3.1 第 4 步切换到 17。
- **缺少 `build-tools;34.0.0`** → 一般 AGP 会自动选用已装的最新 build-tools；若明确报缺，执行 `sdkmanager "build-tools;34.0.0"`。
- **`SSLHandshakeException` / `Connection refused` / Gradle 下载失败** → 网络受限时，Gradle 默认不读系统代理。解决：① 在 `~/.gradle/gradle.properties` 写入
  ```
  systemProp.https.proxyHost=127.0.0.1
  systemProp.https.proxyPort=<你的代理端口>
  ```
  ② 若连 `services.gradle.org` 不通，把 `android/gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成国内镜像（如 `https://mirrors.cloud.tencent.com/gradle/gradle-8.2.1-all.zip`）并设 `validateDistributionUrl=false`。⚠️ **代理端口会随代理软件重启变化**——报 `Connection refused` 时先 `echo $HTTPS_PROXY` 拿当前端口，同步更新上述两处配置。
- **真机不弹 USB 调试授权** → 换数据线、重插，或到系统设置撤销再授权；部分手机需在「USB 配置」选「传输文件」。
- **通知不弹** → Android 13+ 首次使用会请求通知权限，到系统设置给 DAILY 开启「通知」。

## 5. 相关文档

- [README.md](README.md) — 项目简介
- [Technical-Documentation.md](Technical-Documentation.md) — 功能、架构、数据模型
- [Android-Packaging.md](Android-Packaging.md) — 移动端封装方案选型
- [android-app/README.md](android-app/README.md) — 安卓壳工程技术说明（原生桥接实现）
