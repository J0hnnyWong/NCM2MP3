# Makefile for NCM2MP3 (Maven project) —— macOS / Linux
# Windows 请用 run.bat 与 run-flac.bat

# 固定使用 JDK 17 构建：lombok 1.18.22 不兼容过高的 JDK 版本
# 未显式指定时,macOS 上用 java_home 自动挑已安装的 17,Linux 上沿用环境里的 JAVA_HOME
ifeq ($(strip $(JAVA_HOME)),)
DETECTED_JDK17 := $(shell /usr/libexec/java_home -v 17 2>/dev/null)
ifneq ($(strip $(DETECTED_JDK17)),)
export JAVA_HOME := $(DETECTED_JDK17)
endif
else
export JAVA_HOME
endif

ifneq ($(strip $(JAVA_HOME)),)
export PATH := $(JAVA_HOME)/bin:$(PATH)
endif

JAR := target/NCM2MP3-3.1.0.jar
FLAC_JAR := flac-converter/target/flac-converter-1.0.0.jar

.PHONY: all build run clean help setup setup-mac setup-windows flac flac-run flac-clean

all: build

setup-mac:
	brew install openjdk@17 maven ffmpeg

setup: setup-mac

setup-windows:
	@echo "Windows: winget install Microsoft.OpenJDK.17 Gyan.FFmpeg Maven.Maven.3"
	@echo "         然后用 run.bat / run-flac.bat 启动"

flac:
	cd flac-converter && mvn -q package

flac-run: flac
	java -Xmx512m -jar $(FLAC_JAR)

flac-clean:
	cd flac-converter && mvn -q clean

build:
	mvn package

run: build
	java -jar $(JAR) $(ARGS)

clean:
	mvn clean

help:
	@echo "make                                构建主项目 (mvn package)"
	@echo "make run                            构建并运行主项目 (不传参数默认打开 GUI)"
	@echo "make run ARGS=\"-c -f /path/to/ncm\"  命令行转换,-f 重编码 MP3,-k 额外保留 FLAC"
	@echo "make flac                           构建 flac-converter (FLAC 转 MP3 + 元信息回写)"
	@echo "make flac-run                       构建并打开 flac-converter 界面"
	@echo "make clean / make flac-clean        清理构建产物"
	@echo "make setup-mac                      macOS 安装 JDK17/Maven/ffmpeg"
	@echo "make setup-windows                  显示 Windows 对应的 winget 命令"
	@echo ""
	@echo "也可用脚本: ./run.sh [build] [参数] / ./run-flac.sh [build]  (Windows: run.bat / run-flac.bat)"
