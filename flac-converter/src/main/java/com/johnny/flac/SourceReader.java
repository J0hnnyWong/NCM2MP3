package com.johnny.flac;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 源信息读取:FLAC 自带的 Vorbis 标签,外加同目录 .lrc 里的歌词与署名
 *
 * @author johnny
 */
public class SourceReader {

    /**
     * .lrc 署名行里的文本片段:{"t":0,"c":[{"tx":"作词: "},{"tx":"Jefferson, Melissa"}]}
     */
    private static final Pattern TX = Pattern.compile("\"tx\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * 时间轴标记
     */
    private static final Pattern TIME_TAG = Pattern.compile("\\[[0-9]{1,3}:[0-9]{1,3}([.:.][0-9]{1,3})?\\]");

    /**
     * 只含 [ar:xx] [al:xx] 这类文件头信息的行
     */
    private static final Pattern LRC_HEAD = Pattern.compile("^\\s*(\\[[a-zA-Z]+:[^\\]]*\\]\\s*)+$");

    /**
     * 文件头是否为 fLaC:有些曲库里存在把别的格式直接改名成 .flac 的文件,
     * 按真实格式过滤才不会让列表里全是注定失败的条目
     */
    public static boolean isFlac(File file) {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            return magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C';
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 列表展示用:只取 标题/艺人/专辑,不加载封面与歌词;读不出标签时返回空对象
     */
    public static Metadata summary(File flacFile) {        Metadata data = new Metadata();
        try {
            readAudioTags(flacFile, data, false);
        } catch (Exception e) {
            // 展示阶段读不到标签不算错误,转换阶段会给出明确失败原因
        }
        return data;
    }

    /**
     * 转换用:全部标签字段 + 封面 + 歌词
     *
     * @throws Exception 音频或标签无法解析
     */
    public static Metadata full(File flacFile) throws Exception {
        Metadata data = new Metadata();
        readAudioTags(flacFile, data, true);
        applyLrc(flacFile, data);
        return data;
    }

    private static void readAudioTags(File file, Metadata data, boolean deep) throws Exception {
        AudioFile audio = AudioFileIO.read(file);
        Tag tag = audio.getTag();
        if (tag == null) {
            return;
        }
        data.title = first(tag, FieldKey.TITLE);
        for (String artist : every(tag, FieldKey.ARTIST)) {
            data.artists.add(artist);
        }
        data.album = first(tag, FieldKey.ALBUM);
        data.albumArtist = first(tag, FieldKey.ALBUM_ARTIST);
        if (!deep) {
            return;
        }
        data.year = first(tag, FieldKey.YEAR);
        data.genre = first(tag, FieldKey.GENRE);
        data.track = first(tag, FieldKey.TRACK);
        data.disc = first(tag, FieldKey.DISC_NO);
        data.composer = first(tag, FieldKey.COMPOSER);
        data.lyricist = first(tag, FieldKey.LYRICIST);
        data.comment = first(tag, FieldKey.COMMENT);
        data.lyrics = first(tag, FieldKey.LYRICS);
        Artwork artwork = tag.getFirstArtwork();
        if (artwork != null) {
            data.cover = artwork.getBinaryData();
        }
    }

    /**
     * 有同目录 .lrc 时,歌词与作词/作曲以 .lrc 为准(网易云导出的 lrc 比音频内注释更新更全)
     */
    private static void applyLrc(File flacFile, Metadata data) throws Exception {
        File lrcFile = findLrc(flacFile);
        if (lrcFile == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : decode(Files.readAllBytes(lrcFile.toPath())).split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || LRC_HEAD.matcher(line).matches()) {
                continue;
            }
            String[] credit = parseCredit(line);
            if (credit != null) {
                String names = String.join(" / ", splitNames(credit[1]));
                if (credit[0].contains("作词")) {
                    data.lyricist = names;
                } else if (credit[0].contains("作曲")) {
                    data.composer = names;
                } else {
                    // 制作人这类没有通用标准字段的署名,保留进歌词正文,不丢信息
                    lines.add(credit[0] + ": " + names);
                }
                continue;
            }
            if (TIME_TAG.matcher(line).find()) {
                lines.add(line);
            }
        }
        if (!lines.isEmpty()) {
            data.lyrics = String.join("\n", lines);
        }
    }

    /**
     * 解析署名行,返回 [角色, 名单原文];不是署名行时返回 null
     */
    private static String[] parseCredit(String line) {
        if (!line.startsWith("{") || !line.endsWith("}")) {
            return null;
        }
        Matcher matcher = TX.matcher(line);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            text.append(unescape(matcher.group(1)));
        }
        if (text.length() == 0) {
            return null;
        }
        int colon = text.indexOf(":");
        if (colon < 0) {
            colon = text.indexOf("：");
        }
        if (colon <= 0 || colon + 1 >= text.length()) {
            return null;
        }
        String label = text.substring(0, colon).trim();
        String value = text.substring(colon + 1).trim();
        return value.isEmpty() ? null : new String[]{label, value};
    }

    /**
     * 署名名单只能按斜杠拆分:名单内部允许 "Jefferson, Melissa" 这种姓名字序带逗号的单人写法
     */
    private static List<String> splitNames(String credit) {
        List<String> names = new ArrayList<>();
        for (String name : credit.split("/")) {
            if (!name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    /**
     * JSON 字符串字面量反转义
     */
    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 < value.length()) {
                        out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }

    private static File findLrc(File flacFile) {
        String name = flacFile.getName();
        int dot = name.lastIndexOf('.');
        File parent = flacFile.getParentFile();
        if (dot <= 0 || parent == null) {
            return null;
        }
        String base = name.substring(0, dot);
        File lower = new File(parent, base + ".lrc");
        if (lower.isFile()) {
            return lower;
        }
        File upper = new File(parent, base + ".LRC");
        return upper.isFile() ? upper : null;
    }

    /**
     * 优先按 UTF-8 严格解码,失败回退 GBK
     */
    private static String decode(byte[] bytes) throws CharacterCodingException {
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static String first(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return value == null || value.trim().isEmpty() ? null : value.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> every(Tag tag, FieldKey key) {
        List<String> values = new ArrayList<>();
        try {
            for (String value : tag.getAll(key)) {
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                }
            }
        } catch (Exception e) {
            String single = first(tag, key);
            if (single != null) {
                values.add(single);
            }
        }
        return values;
    }
}
