package service.tag;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 从解密后的音频文件中读取全部源标签。
 * NCM解密出的原始音频自带完整的标签(FLAC的Vorbis Comment / MP3的ID3),
 * 转换前先把这些标签读出,转换后再写回目标文件。
 *
 * @author charlottexiao
 */
public class AudioTagExtractor {

    /**
     * 读取音频文件中的全部标签,解析失败时返回空对象而不抛异常
     *
     * @param audioFile 解密后的音频文件
     * @return 标签信息
     */
    public static TagInfo extract(File audioFile) {
        TagInfo info = new TagInfo();
        Tag tag;
        try {
            AudioFile audio = AudioFileIO.read(audioFile);
            tag = audio.getTag();
        } catch (Exception e) {
            System.out.format("读取源标签失败(跳过)：%s (%s)%n", audioFile.getName(), e.getMessage());
            return info;
        }
        if (tag == null) {
            return info;
        }
        info.title = first(tag, FieldKey.TITLE);
        info.artists = all(tag, FieldKey.ARTIST);
        info.albumArtist = first(tag, FieldKey.ALBUM_ARTIST);
        info.album = first(tag, FieldKey.ALBUM);
        info.year = first(tag, FieldKey.YEAR);
        info.genre = first(tag, FieldKey.GENRE);
        info.track = first(tag, FieldKey.TRACK);
        info.disc = first(tag, FieldKey.DISC_NO);
        info.composer = first(tag, FieldKey.COMPOSER);
        info.lyricist = first(tag, FieldKey.LYRICIST);
        info.comment = first(tag, FieldKey.COMMENT);
        info.lyrics = first(tag, FieldKey.LYRICS);
        try {
            Artwork artwork = tag.getFirstArtwork();
            if (artwork != null) {
                info.cover = artwork.getBinaryData();
            }
        } catch (Exception e) {
            System.out.format("读取源封面失败(跳过)：%s (%s)%n", audioFile.getName(), e.getMessage());
        }
        return info;
    }

    private static String first(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return isBlank(value) ? null : value.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] all(Tag tag, FieldKey key) {
        List<String> values = new ArrayList<>();
        try {
            for (String value : tag.getAll(key)) {
                if (!isBlank(value)) {
                    values.add(value.trim());
                }
            }
        } catch (Exception e) {
            String single = first(tag, key);
            if (!isBlank(single)) {
                values.add(single);
            }
        }
        return values.toArray(new String[0]);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
