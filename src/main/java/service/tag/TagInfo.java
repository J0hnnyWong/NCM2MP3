package service.tag;

/**
 * 待写入音频文件的标签信息
 *
 * @author charlottexiao
 */
public class TagInfo {

    /**
     * 音乐名
     */
    public String title;

    /**
     * 艺术家
     */
    public String[] artists;

    /**
     * 专辑
     */
    public String album;

    /**
     * 封面图片数据
     */
    public byte[] cover;
}
