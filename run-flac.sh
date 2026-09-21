#!/bin/sh
# flac-converter 启动脚本(macOS / Linux)。Windows 请用 run-flac.bat。
# 用法: ./run-flac.sh          打开界面
#       ./run-flac.sh build    强制重新构建后再打开
set -e

ROOT=$(cd "$(dirname "$0")" && pwd)
SUB="$ROOT/flac-converter"
JAR="$SUB/target/flac-converter-1.0.0.jar"

# ---------- 定位 JDK(lombok 不需要,但构建仍走 Maven,17 最稳) ----------
find_java() {
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME"; return 0
  fi
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    jh=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
    if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then echo "$jh"; return 0; fi
  fi
  for d in /usr/lib/jvm/java-17-* /usr/local/opt/openjdk@17 /opt/homebrew/opt/openjdk@17 /Library/Java/JavaVirtualMachines/*17*/Contents/Home; do
    if [ -x "$d/bin/java" ]; then echo "$d"; return 0; fi
  done
  return 1
}

JDK=$(find_java || true)
if [ -n "$JDK" ]; then
  JAVA_HOME="$JDK"; export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"; export PATH
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA=$(command -v java)
else
  echo "[ERROR] 未找到 Java。macOS: brew install openjdk@17"
  exit 1
fi

# ---------- 构建 ----------
FORCE=0
if [ "$1" = "build" ]; then
  FORCE=1
fi

if [ "$FORCE" = "1" ] || [ ! -f "$JAR" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN=mvn
  else
    echo "[ERROR] 缺少 jar 且未找到 Maven,无法构建。brew install maven"
    exit 1
  fi
  echo "[INFO] 使用 $MVN 构建 flac-converter..."
  (cd "$SUB" && "$MVN" -q package)
fi

# ---------- 启动 ----------
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "[WARN] PATH 里没有找到 ffmpeg,界面里转码会不可用。macOS: brew install ffmpeg"
fi
echo "[INFO] 启动 FLAC 元信息转换..."
exec "$JAVA" -Xmx512m -jar "$JAR"
