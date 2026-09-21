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

    /** 重编码为 MP3 时,是否把解密出的原始 FLAC 也留在输出目录 */
    public boolean keepFlacWithMp3 = false;

    public ConvertOptions() {
    }

    public ConvertOptions(TagMode tagMode, boolean reEncodeWithFfmpeg) {
        this.tagMode = tagMode;
        this.reEncodeWithFfmpeg = reEncodeWithFfmpeg;
    }

    public ConvertOptions(TagMode tagMode, boolean reEncodeWithFfmpeg, boolean keepFlacWithMp3) {
        this.tagMode = tagMode;
        this.reEncodeWithFfmpeg = reEncodeWithFfmpeg;
        this.keepFlacWithMp3 = keepFlacWithMp3;
    }

    public static ConvertOptions defaults() {
        return new ConvertOptions();
    }
}
