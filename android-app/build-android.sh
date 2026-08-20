#!/usr/bin/env bash
# 一键构建 DAILY 安卓 APK（自用侧载）
# 用法：
#   bash build-android.sh          # 默认 debug（debug 签名，可直接 adb 侧载）
#   bash build-android.sh debug     # 同上
#   bash build-android.sh release   # release（需先配签名 keystore，见 README）
set -e
cd "$(dirname "$0")"
MODE="${1:-debug}"

echo "==> 1/4 安装/更新 npm 依赖"
[ -d node_modules ] || npm install

echo "==> 2/4 同步最新 HTML 到 web/"
bash sync-web.sh

echo "==> 2.5 拷贝 Capacitor 插件 web 运行时到 web/capacitor-plugins/（离线可用，供 HTML 内 capLoad 动态加载）"
mkdir -p web/capacitor-plugins
for pair in "local-notifications:LocalNotifications" "share:Share" "filesystem:Filesystem"; do
  pkg="${pair%%:*}"; name="${pair##*:}"; found=""
  for cand in "node_modules/@capacitor/$pkg/dist/plugin.js" "node_modules/@capacitor/$pkg/dist/esm/index.js" "node_modules/@capacitor/$pkg/plugin.js"; do
    if [ -f "$cand" ]; then found="$cand"; break; fi
  done
  if [ -n "$found" ]; then
    cp "$found" "web/capacitor-plugins/$name.js"
    echo "  拷入 capacitor-plugins/$name.js  <-  $found"
  else
    echo "  ! 未找到 @capacitor/$pkg 的 web 运行时（npm install 是否成功？），原生通知/分享将不可用"
  fi
done

echo "==> 3/4 同步 Capacitor 资源 + 首次自动添加 android 平台"
npx cap sync android 2>/dev/null || true
if [ ! -d android ]; then
  npx cap add android
  npx cap sync android
fi

echo "==> 4/4 Gradle 构建 $MODE APK"
cd android
if [ "$MODE" = "release" ]; then
  ./gradlew assembleRelease
  APK="app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew assembleDebug
  APK="app/build/outputs/apk/debug/app-debug.apk"
fi

cd ..
echo ""
echo "✓ 构建完成"
echo "  APK: android/$APK"
echo "  侧载: adb install -r android/$APK"
