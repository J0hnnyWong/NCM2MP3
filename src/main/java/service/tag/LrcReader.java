package service.tag;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 读取NCM同目录下的 .lrc 歌词文件。
 * 网易云的 .lrc 头部会带若干行 {"t":..,"c":[{"tx":"作词: .."},..]} 形式的署名信息,
 * 这些行用于补齐 作词/作曲,歌词正文只保留带时间轴的歌词行
 *
 * @author charlottexiao
 */
public class LrcReader {

    /**
     * 只含 [ar:xx] [al:xx] 这类文件头信息的行
     */
    private static final Pattern ID_TAG = Pattern.compile("^\\s*(\\[[a-zA-Z]+:[^\\]]*\\]\\s*)+$");

    /**
     * 时间轴标记
     */
    private static final Pattern TIME_TAG = Pattern.compile("\\[[0-9]{1,3}:[0-9]{1,3}([.:.][0-9]{1,3})?\\]");

    /**
     * 署名信息,如 "作词: xx/yy"
     */
    private static final String CREDIT_LYRICIST = "作词";

    private static final String CREDIT_COMPOSER = "作曲";

    /**
     * 读取与NCM文件同名的 .lrc,解析出歌词正文与署名信息
     *
     * @param ncmFile NCM文件
     * @return 只填充了 lyrics/lyricist/composer 的标签对象,永不为null
     */
    public static TagInfo read(File ncmFile) {
        TagInfo info = new TagInfo();
        File lrcFile = findLrcFile(ncmFile);
        if (lrcFile == null) {
            return info;
        }
        try {
            String content = decode(Files.readAllBytes(lrcFile.toPath()));
            List<String> lines = new ArrayList<>();
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.trim();
                if (line.isEmpty() || ID_TAG.matcher(line).matches()) {
                    continue;
                }
                String[] credit = parseCredit(line);
                if (credit != null) {
                    String[] names = ArtistNames.fromCredit(credit[1]);
                    if (names.length > 0) {
                        String joined = String.join(" / ", names);
                        if (credit[0].contains(CREDIT_LYRICIST)) {
                            info.lyricist = joined;
                        } else if (credit[0].contains(CREDIT_COMPOSER)) {
                            info.composer = joined;
                        } else {
                            // 制作人这类没有通用标准字段的署名,保留进歌词正文,不丢信息
                            lines.add(credit[0] + ": " + joined);
                        }
                    }
                    continue;
                }
                if (TIME_TAG.matcher(line).find()) {
                    lines.add(line);
                }
            }
            info.lyrics = lines.isEmpty() ? null : String.join("\n", lines);
            System.out.format("读取歌词：%s%n", lrcFile.getName());
        } catch (Exception e) {
            System.out.format("歌词读取失败(跳过)：%s (%s)%n", lrcFile.getName(), e.getMessage());
        }
        return info;
    }

    /**
     * 解析 {"t":0,"c":[{"tx":"作词: "},{"tx":"Jefferson, Melissa"}]} 形式的署名行
     *
     * @return [角色, 名单原文],不是署名行时返回null
     */
    private static String[] parseCredit(String line) {
        try {
            JSONArray parts = JSON.parseObject(line).getJSONArray("c");
            if (parts == null) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                JSONObject part = parts.getJSONObject(i);
                String tx = (part == null) ? null : part.getString("tx");
                if (tx != null) {
                    text.append(tx);
                }
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
        } catch (Exception e) {
            // 不是署名JSON行,忽略
            return null;
        }
    }

    /**
     * 大小写不敏感地寻找同名 .lrc 文件
     */
    private static File findLrcFile(File ncmFile) {
        String name = ncmFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        File parent = ncmFile.getParentFile();
        if (parent == null) {
            return null;
        }
        File exact = new File(parent, name.substring(0, dot) + ".lrc");
        if (exact.isFile()) {
            return exact;
        }
        File upper = new File(parent, name.substring(0, dot) + ".LRC");
        return upper.isFile() ? upper : null;
    }

    /**
     * 优先按UTF-8严格解码,失败则回退GBK
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
}
