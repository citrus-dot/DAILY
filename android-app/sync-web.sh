#!/usr/bin/env bash
# 把最新的「DAILY.html」同步进 web/，作为 Capacitor 的 webDir 内容
set -e
cd "$(dirname "$0")"
mkdir -p web
cp "../DAILY.html" "web/index.html"
echo "[sync] web/index.html <- ../DAILY.html ($(date +%Y-%m-%d_%H:%M:%S))"
