package com.johnny.flac;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 把源信息写回转换后的 MP3(ffmpeg 编码时被 -map_metadata -1 清掉的部分在这里全部补回来)
 *
 * @author johnny
 */
public class TagWriter {

    private static final int PICTURE_TYPE_FRONT_COVER = 3;

    /**
     * 写入全部字段与封面
     *
     * @return 因格式限制没写进去的字段名,便于界面提示
     */
    public static List<String> write(File mp3File, Metadata data) throws Exception {
        List<String> skipped = new ArrayList<>();
        AudioFile audio = AudioFileIO.read(mp3File);
        Tag tag = audio.getTagOrCreateDefault();
        if (tag == null) {
            throw new IllegalStateException("无法为该文件创建标签: " + mp3File.getName());
        }
        audio.setTag(tag);
        put(tag, skipped, FieldKey.TITLE, data.title);
        put(tag, skipped, FieldKey.ARTIST, data.artistText());
        put(tag, skipped, FieldKey.ALBUM, data.album);
        put(tag, skipped, FieldKey.ALBUM_ARTIST, data.albumArtist);
        put(tag, skipped, FieldKey.YEAR, leadingDigits(data.year));
        put(tag, skipped, FieldKey.GENRE, data.genre);
        put(tag, skipped, FieldKey.TRACK, leadingDigits(data.track));
        put(tag, skipped, FieldKey.DISC_NO, leadingDigits(data.disc));
        put(tag, skipped, FieldKey.COMPOSER, data.composer);
        put(tag, skipped, FieldKey.LYRICIST, data.lyricist);
        put(tag, skipped, FieldKey.COMMENT, data.comment);
        put(tag, skipped, FieldKey.LYRICS, data.lyrics);
        writeCover(tag, skipped, data.cover);
        AudioFileIO.write(audio);
        return skipped;
    }

    private static void put(Tag tag, List<String> skipped, FieldKey key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        try {
            tag.setField(key, value);
        } catch (Exception e) {
            skipped.add(key.name());
        }
    }

    /**
     * 封面固定用"前封面"类型,部分便携播放器只认这一种
     */
    private static void writeCover(Tag tag, List<String> skipped, byte[] cover) {
        if (cover == null || cover.length == 0) {
            return;
        }
        try {
            Artwork artwork = ArtworkFactory.getNew();
            artwork.setBinaryData(cover);
            artwork.setMimeType(isPng(cover) ? "image/png" : "image/jpeg");
            artwork.setPictureType(PICTURE_TYPE_FRONT_COVER);
            artwork.setDescription("");
            tag.setField(artwork);
        } catch (Exception e) {
            skipped.add("COVER");
        }
    }

    private static boolean isPng(byte[] image) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (image.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (image[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 只保留开头的数字,用于年份/音轨号/碟片号这类要求纯数字的字段("1/12" 取 "1")
     */
    private static String leadingDigits(String value) {
        if (value == null) {
            return null;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return end == 0 ? null : value.substring(0, end);
    }
}
