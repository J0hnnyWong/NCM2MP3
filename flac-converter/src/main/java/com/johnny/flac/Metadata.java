package com.johnny.flac;

import java.util.ArrayList;
import java.util.List;

/**
 * 一首歌从源文件里读到的全部元信息
 *
 * @author johnny
 */
public class Metadata {

    public String title;

    /**
     * 合作演唱者全部保留,写回时按 " / " 连接
     */
    public final List<String> artists = new ArrayList<>();

    public String album;

    public String albumArtist;

    public String year;

    public String genre;

    public String track;

    public String disc;

    public String composer;

    public String lyricist;

    public String comment;

    public String lyrics;

    public byte[] cover;

    /**
     * 艺人字段文本,多人合并为一个值,兼容只认单值艺人的播放器
     */
    public String artistText() {
        return artists.isEmpty() ? null : String.join(" / ", artists);
    }
}
