package mime;

/**
 * NCM内置元数据(容器中JSON块的全部已知字段)
 *
 * @author charlottexiao
 */
public class Mata {

    /**
     * 音乐名
     */
    public String musicName;

    /**
     * 艺术家,每项为 [名称, 艺人ID]
     */
    public String[][] artist;

    /**
     * 专辑
     */
    public String album;

    /**
     * 格式
     */
    public String format;

    /**
     * 音乐ID
     */
    public String musicId;

    /**
     * 专辑ID
     */
    public String albumId;

    /**
     * 专辑封面文档ID
     */
    public String albumPicDocId;

    /**
     * 专辑封面地址
     */
    public String albumPic;

    /**
     * mp3文档ID
     */
    public String mp3DocId;

    /**
     * 时长(毫秒)
     */
    public Long duration;

    /**
     * 码率(bps)
     */
    public Long bitrate;

    /**
     * MV ID
     */
    public String mvId;

    /**
     * 别名
     */
    public String[] alias;

    /**
     * 译名
     */
    public String[] transNames;

    /**
     * 付费类型
     */
    public Integer fee;

    /**
     * 音量补偿
     */
    public Double volumeDelta;
}
