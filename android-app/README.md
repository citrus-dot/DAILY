# DAILY · 安卓壳工程（Capacitor）

> 把 `DAILY.html`（单文件）封装为安卓桌面应用。完全本地、离线、可侧载。路线：**Capacitor + 本地通知 + 分享**。
> 完整的环境搭建与构建排障见根目录 [onboarding_guide.md](../onboarding_guide.md)。

## 目录
- `package.json` — Capacitor 依赖
- `capacitor.config.json` — 应用配置（appId `cn.orange.daily`，webDir `web`）
- `sync-web.sh` — 把上层 `DAILY.html` 同步到 `web/index.html`
- `build-android.sh` — 一键构建 APK（debug/release）
- `README.md` — 本文件
- `android/` — 首次构建时由 `cap add android` 自动生成（不手写）

---

## 一、前置环境（一次性安装）

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | ≥ 22 | 跑 Capacitor CLI |
| Android Studio | Hedgehog+/2024.x | 含 Android SDK、Platform 34、Build-Tools 34.x |
| JDK | 17 | Android Studio 自带或独立安装，设 `JAVA_HOME` 指向它 |
| Android SDK | API 34（minSdk 24） | 在 Studio 的 SDK Manager 装 |
| 平台工具 | adb、platform-tools | Studio 自带；设 `ANDROID_HOME` |

**环境变量**（写进 `~/.zshrc`；JDK 17 的获取方式见 onboarding_guide）：
```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="<你的 JDK 17 路径>"   # 如 /Applications/Android Studio.app/Contents/jbr/Contents/Home
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```
装好后验证：`adb --version`、`java -version`（17）、`echo $ANDROID_HOME`。

---

## 二、一键构建（装好环境后）

在仓库根目录执行：

```sh
cd android-app
bash build-android.sh            # 默认 debug，产出 app-debug.apk
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

脚本流程：`npm install` → 同步最新 HTML → `cap sync` →（首次）`cap add android` → `gradlew assembleDebug`。
修改 HTML 后只需重跑 `bash build-android.sh` 即可刷新 APK。

用 Android Studio 调试/改原生（图标、应用名、权限）：`npm run open`。

---

## 三、原生能力设计（已在 HTML 内嵌实现，浏览器自动静默降级）

> 全部桥接代码已写入 `DAILY.html` 的 `<script>`，通过 `IS_NATIVE`（`window.Capacitor.isNativePlatform()`）判断是否原生端。**浏览器中一切无副作用**：通知不排程、导出走普通下载、文件接收钩子不触发。无需安卓环境即可在浏览器预览设置 UI。

### 1. 每日打卡本地通知
**设置存储**（ADD-only，兼容 `wb_daily_state`）：
```js
STATE.settings.reminder = { on:false, hour:21, minute:0 };
```
**设置 UI**：设置菜单新增「每日提醒」行——开关（`.set-toggle#reminderToggle`）+ 时间选择器（`<input type="time" id="reminderTime">`）。切换即写 `STATE.settings.reminder` 并立即 `capApplyReminder()`。

**核心函数**（节选自 HTML）：
```js
const IS_NATIVE = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
const REMINDER_ID = 1001;

async function capLoad(name){               // 取插件：全局注入优先，否则原生端动态 import 本地 vendored 运行时
  try{ if(window.Capacitor?.Plugins?.[name]) return window.Capacitor.Plugins[name]; }catch(e){}
  if(!IS_NATIVE) return null;
  for(const p of ['./capacitor-plugins/'+name+'.js','./capacitor-plugins/'+name+'/index.js']){
    try{ await import(p); if(window.Capacitor?.Plugins?.[name]) return window.Capacitor.Plugins[name]; }catch(e){}
  }
  return null;
}
async function capApplyReminder(){
  const r = getReminder();                  // 带范围校验，损坏字段回退默认
  if(!r.on){ const LN=await capLoad('LocalNotifications'); if(LN) await LN.cancel({notifications:[{id:REMINDER_ID}]}); return; }
  const LN = await capLoad('LocalNotifications'); if(!LN) return;
  let p = await LN.checkPermissions();
  if(p.display!=='granted') p = await LN.requestPermissions();
  if(p.display!=='granted') return;
  await LN.cancel({notifications:[{id:REMINDER_ID}]});
  await LN.schedule({ notifications:[{
    id: REMINDER_ID, title:'DAILY · 每日打卡',
    body:'今天的目标完成了吗？点开记录一下吧 🌿',
    schedule:{ on:{ hour:r.hour, minute:r.minute }, repeats:true }, sound:'default'
  }]});
}
```
**调用时机**：开/关提醒、改时间时立即调用；原生端 `load()` 后若 `reminder.on` 为真则 `capApplyReminder()` 恢复排程（设备重启后也生效，插件内置 boot receiver）。

> 插件 web 运行时由 `build-android.sh` 的 2.5 步拷贝到 `web/capacitor-plugins/`，`cap sync` 一并打进原生包，故完全离线可用。若某插件路径不同，调 `build-android.sh` 时会有 `! 未找到` 警告，按需补候选路径即可。

### 2. 分享 / 接收文件
**导出分支**（JSON/CSV/MD 统一走 `capDownloadOrShare`）：原生端用 Filesystem 写缓存文件 + Share 系统分享面板，浏览器端走普通下载：
```js
function downloadBlob(content, filename, mime){ /* 浏览器下载：Blob + a.click() */ }
async function capDownloadOrShare(content, filename, mime){
  if(!IS_NATIVE){ downloadBlob(content, filename, mime); return; }
  const Share = await capLoad('Share'); if(!Share){ downloadBlob(content, filename, mime); return; }
  const Filesystem = await capLoad('Filesystem');
  if(Filesystem){
    await Filesystem.writeFile({ path:filename, data:content, directory:'CACHE', recursive:true });
    const uri = await Filesystem.getUri({ path:filename, directory:'CACHE' });
    await Share.share({ title:'DAILY 导出', text:filename, files:[uri.uri] });
  } else { await Share.share({ title:'DAILY 导出', text:content }); }
}
```
**接收文件**（从其它 App「分享到 DAILY」打开 .json 自动导入）：
1. 在原生工程 `android/app/src/main/AndroidManifest.xml` 的 `<activity>` 内加 intent-filter：
   ```xml
   <intent-filter>
     <action android:name="android.intent.action.SEND" />
     <category android:name="android.intent.category.DEFAULT" />
     <data android:mimeType="application/json" />
   </intent-filter>
   ```
2. 原生侧把收到的文件文本交给 WebView：在 `MainActivity` 的 `onNewIntent` 里 `bridge.evaluateJavascript("window.capOnSharedFile(` + JSONObject.quote(text) + `)", null);`
3. HTML 已暴露 `window.capOnSharedFile = function(text){ ... }`（内部走 `sanitizeState` 后导入并 `renderAll`），无需改动 HTML 即可对接。
> 这步需原生工程存在后做（即你装好 Android Studio、跑过首次 `build-android.sh` 之后）。

---

## 四、release 签名（已配置，2026-08-20）

**当前已配置完成**：`android-app/daily-release.keystore`（alias `daily`，RSA 2048，36,500 天）已生成；密码/路径存于 `~/.gradle/gradle.properties`（`DAILY_STORE_FILE` / `DAILY_STORE_PASSWORD` / `DAILY_KEY_ALIAS` / `DAILY_KEY_PASSWORD`）；`android/app/build.gradle` 的 `signingConfigs.release` 从其中读取（不进仓库）；keystore 已被 `.gitignore` 保护（`*.keystore`）。

构建与安装：

```sh
bash build-android.sh release
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

- **务必备份 `daily-release.keystore`**（丢失 = 无法更新/上架）。
- ⚠️ release 与 debug 签名不同，**不可覆盖升级**：已装 debug 版需先卸载（数据会清空，先导出 JSON 备份）；此后统一用 release 包更新（同签名无缝升级）。

> 若重装 keystore（换机/重做），按上面 `keytool` 命令重新生成并把密码写回 `~/.gradle/gradle.properties` 的 `DAILY_*` 键即可；切勿把密码写进仓库。

---

## 五、注意事项与已知边界

- **数据持久**：WebView 的 `localStorage` 在「清缓存 / 卸载应用」时会丢——已有的导出/导入备份正好兜底；建议开通知后提醒用户定期导出。
- **字体体积**：内嵌 LXGW WenKai ~6MB，APK 会偏大但可接受；若要瘦身，把 `@font-face` 改为系统字体（`PingFang`/`-apple-system`）回退。
- **WebView 版本**：跟随系统 Chrome（Android 7.0+ / API 24 起够用，`minSdk=24` 已在 Capacitor 默认）。
- **跨天刷新**：HTML 内已有 30s 定时器（F45），原生壳内同样生效。
- **图标**：`npx cap add android` 后用 Android Studio 的 Image Asset 配自适应图标（前台 `DAILY` 字样/勾号，背景 `#3b82f6`）；`capacitor.config.json` 里 `LocalNotifications.smallIcon` 指向 `ic_stat_notify`，需在 `android/app/src/main/res/drawable-*` 放一个白色单色通知小图。

---

## 六、进度与下一步
- ✅ **已完成（F46）**：`DAILY.html` 内嵌 Capacitor 桥接——「每日提醒」设置 UI + `capApplyReminder()` 本地通知 + `capDownloadOrShare()` 导出分享 + `window.capOnSharedFile` 文件接收钩子；浏览器中全部静默降级。`build-android.sh` 已加 2.5 步拷贝插件 web 运行时；本 README §三 已与实现同步。
- ✅ **已完成（F101）**：debug 侧载 + **release 签名版**均已产出并安装真机（详见 §四）；当前 `app-release.apk`（7.5MB）为正式分发版本。
- ⏳ **可选下一步**：AndroidManifest `action.SEND` intent-filter + `MainActivity.onNewIntent` 对接文件接收（详见 §三.2）；真机验证文件接收导入；如需上架 Play 用 release 包出 AAB。
