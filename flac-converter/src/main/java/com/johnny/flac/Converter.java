package com.johnny.flac;

import java.io.File;
import java.util.List;

/**
 * 一首歌的完整流程:读出源信息 → 编码 MP3 → 把源信息写回去
 *
 * @author johnny
 */
public class Converter {

    /**
     * 单首转换结果,用于界面状态列
     */
    public static class Result {

        private final long size;

        private final List<String> skippedFields;

        Result(long size, List<String> skippedFields) {
            this.size = size;
            this.skippedFields = skippedFields;
        }

        public String statusText() {
            if (skippedFields.isEmpty()) {
                return "成功 " + readableSize();
            }
            return "成功 " + readableSize() + "(未写入 " + String.join("/", skippedFields) + ")";
        }

        private String readableSize() {
            long kb = size / 1024;
            return kb >= 1024 ? String.format("%.1fMB", kb / 1024.0) : kb + "KB";
        }
    }

    /**
     * @param source 源 FLAC
     * @param target 目标 MP3
     * @throws Exception 标签读取、编码或写回失败
     */
    public static Result convert(File source, File target) throws Exception {
        Metadata data = SourceReader.full(source);
        Mp3Encoder.encode(source, target);
        List<String> skipped = TagWriter.write(target, data);
        return new Result(target.length(), skipped);
    }
}
