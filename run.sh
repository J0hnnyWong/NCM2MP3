#!/bin/sh
# NCM2MP3 启动脚本(macOS / Linux)。Windows 请用 run.bat。
# 用法: ./run.sh                 打开界面
#       ./run.sh build           强制重新构建后再打开
#       ./run.sh -c -f <path>    命令行转换(-k 额外保留 FLAC)
set -e

ROOT=$(cd "$(dirname "$0")" && pwd)
JAR="$ROOT/target/NCM2MP3-3.1.0.jar"

# ---------- 定位 JDK 17(lombok 1.18.22 不兼容过高的 JDK) ----------
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
  echo "[WARN] 未找到 JDK 17,使用 PATH 里的 java;若构建失败请设置 JAVA_HOME 指向 JDK 17"
else
  echo "[ERROR] 未找到 Java。macOS: brew install openjdk@17"
  exit 1
fi

# ---------- 构建 ----------
FORCE=0
if [ "$1" = "build" ]; then
  FORCE=1
  shift
fi

if [ "$FORCE" = "1" ] || [ ! -f "$JAR" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN=mvn
  elif [ -x "$ROOT/mvnw" ]; then
    MVN="$ROOT/mvnw"
  else
    echo "[ERROR] 缺少 jar 且未找到 Maven,无法构建。brew install maven"
    exit 1
  fi
  echo "[INFO] 使用 $MVN 构建..."
  (cd "$ROOT" && "$MVN" -q package)
fi

# ---------- 启动 ----------
echo "[INFO] 启动 NCM2MP3..."
exec "$JAVA" -jar "$JAR" "$@"
