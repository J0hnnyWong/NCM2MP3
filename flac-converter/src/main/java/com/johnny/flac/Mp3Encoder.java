package com.johnny.flac;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 调用 ffmpeg 把 FLAC 编成 320kbps CBR 的 MP3。
 * 音频从标准输入分块喂进去:一是避开中文路径在个别 ffmpeg 构建上的解码问题,
 * 二是整首音频不会进堆,并发转换才不会耗尽内存
 *
 * @author johnny
 */
public class Mp3Encoder {

    /**
     * 输出码率
     */
    public static final String BITRATE = "320k";

    private static final long TIMEOUT_MINUTES = 10;

    /**
     * ffmpeg 是否可用
     */
    public static boolean available() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-version");
            builder.redirectErrorStream(true);
            process = builder.start();
            // 先把输出抽干再等结束,否则小管道填满会互相卡住
            drain(process.getInputStream(), null);
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 编码一首歌
     *
     * @param source 源 FLAC
     * @param target 目标 MP3,失败时不会残留半成品
     * @throws Exception ffmpeg 缺失、超时或输出不合法
     */
    public static void encode(File source, File target) throws Exception {
        File parent = target.getParentFile();
        // 同一张专辑的多首歌并发时会同时建同一个目录,建好了就算成功
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        ProcessBuilder builder = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-i", "pipe:0",
                "-vn",
                "-codec:a", "libmp3lame",
                "-b:a", BITRATE,
                "-map_metadata", "-1",
                target.getAbsolutePath()
        );
        builder.redirectErrorStream(true);
        Process process = null;
        try {
            process = builder.start();
            final Process running = process;
            final StringBuilder ffmpegLog = new StringBuilder();
            Thread reader = new Thread(() -> drain(running.getInputStream(), ffmpegLog), "ffmpeg-log");
            reader.setDaemon(true);
            reader.start();

            feedInput(source, process);
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IOException("ffmpeg 超时(超过 " + TIMEOUT_MINUTES + " 分钟)");
            }
            reader.join(TimeUnit.SECONDS.toMillis(10));
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg 退出码 " + process.exitValue() + message(ffmpegLog));
            }
            if (!target.isFile() || target.length() < 1024) {
                throw new IOException("ffmpeg 输出不合法" + message(ffmpegLog));
            }
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            deleteQuietly(target);
            throw e;
        }
    }

    /**
     * 分块写入 ffmpeg 标准输入,写完关闭让对方看到 EOF
     */
    private static void feedInput(File source, Process process) throws IOException {
        byte[] buffer = new byte[0x8000];
        try (InputStream in = new FileInputStream(source);
             OutputStream out = process.getOutputStream()) {
            for (int read; (read = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    /**
     * 读空子进程输出避免管道阻塞,需要时保留末尾若干字符作为失败原因
     */
    private static void drain(InputStream stream, StringBuilder tail) {
        byte[] buffer = new byte[0x2000];
        try (InputStream in = stream) {
            for (int read; (read = in.read(buffer)) > 0; ) {
                if (tail == null) {
                    continue;
                }
                tail.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                if (tail.length() > 2000) {
                    tail.delete(0, tail.length() - 2000);
                }
            }
        } catch (IOException e) {
            // 子进程已退出或被杀掉,读不到就算了
        }
    }

    private static String message(StringBuilder log) {
        String text = log.toString().trim();
        return text.isEmpty() ? "" : ": " + text;
    }

    private static void deleteQuietly(File file) {
        if (file.isFile()) {
            file.delete();
        }
    }
}
