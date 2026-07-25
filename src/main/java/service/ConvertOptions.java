package service;

import service.tag.TagMode;

/**
 * 转换高级配置选项
 */
public class ConvertOptions {

    /** 标签信息来源模式 */
    public TagMode tagMode = TagMode.NCM;

    /** 是否使用 ffmpeg 重新编码为 MP3 */
    public boolean reEncodeWithFfmpeg = false;

    public ConvertOptions() {
    }

    public ConvertOptions(TagMode tagMode, boolean reEncodeWithFfmpeg) {
        this.tagMode = tagMode;
        this.reEncodeWithFfmpeg = reEncodeWithFfmpeg;
    }

    public static ConvertOptions defaults() {
        return new ConvertOptions();
    }
}
