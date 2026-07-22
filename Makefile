# Makefile for NCM2MP3 (Maven project)

JAR := target/NCM2MP3-3.1.0.jar

.PHONY: all build run clean help

all: build

build:
	mvn package

run: build
	java -jar $(JAR) $(ARGS)

clean:
	mvn clean

help:
	@echo "make                                构建项目 (mvn package)"
	@echo "make run                            构建并运行 (不传参数默认打开 GUI)"
	@echo "make run ARGS=\"-c /path/to/ncm\"     命令行转换文件或目录"
	@echo "make clean                          清理 target 构建产物"
