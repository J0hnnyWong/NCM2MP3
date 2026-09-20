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
     * 专辑艺人
     */
    public String albumArtist;

    /**
     * 发行年份
     */
    public String year;

    /**
     * 流派
     */
    public String genre;

    /**
     * 音轨号
     */
    public String track;

    /**
     * 碟片号
     */
    public String disc;

    /**
     * 作曲
     */
    public String composer;

    /**
     * 作词
     */
    public String lyricist;

    /**
     * 备注
     */
    public String comment;

    /**
     * 歌词
     */
    public String lyrics;

    /**
     * 封面图片数据
     */
    public byte[] cover;

    /**
     * 用 other 中的非空字段补齐当前对象缺失的信息(已有值不覆盖)
     *
     * @param other 兜底来源,可为null
     */
    public void fillBlanksFrom(TagInfo other) {
        if (other == null) {
            return;
        }
        if (isBlank(title)) {
            title = other.title;
        }
        if (isEmpty(artists) && !isEmpty(other.artists)) {
            artists = other.artists;
        }
        if (isBlank(album)) {
            album = other.album;
        }
        if (isBlank(albumArtist)) {
            albumArtist = other.albumArtist;
        }
        if (isBlank(year)) {
            year = other.year;
        }
        if (isBlank(genre)) {
            genre = other.genre;
        }
        if (isBlank(track)) {
            track = other.track;
        }
        if (isBlank(disc)) {
            disc = other.disc;
        }
        if (isBlank(composer)) {
            composer = other.composer;
        }
        if (isBlank(lyricist)) {
            lyricist = other.lyricist;
        }
        if (isBlank(comment)) {
            comment = other.comment;
        }
        if (isBlank(lyrics)) {
            lyrics = other.lyrics;
        }
        if (cover == null || cover.length == 0) {
            cover = other.cover;
        }
    }

    /**
     * 拼接艺术家列表,单个字段兼容只支持一文多人的播放器
     */
    public String joinedArtists() {
        if (isEmpty(artists)) {
            return null;
        }
        return String.join(" / ", artists);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isEmpty(String[] values) {
        if (values == null || values.length == 0) {
            return true;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return false;
            }
        }
        return true;
    }
}
