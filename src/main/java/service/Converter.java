package service;

import com.alibaba.fastjson.JSON;
import mime.Mata;
import mime.Ncm;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import service.tag.AudioTagExtractor;
import service.tag.LrcReader;
import service.tag.NcmTagProvider;
import service.tag.PathTagProvider;
import service.tag.TagInfo;
import service.tag.TagMode;
import service.tag.TagProvider;
import utils.AES;
import utils.CR4;
import utils.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * @author charlottexiao
 */
public class Converter {
    /**
     * NCM转换MP3
     * 功能:将NCM音乐转换为MP3
     *
     * @param ncmFilePath NCM文件路径
     * @param outFilePath MP3文件路径
     * @return 转换成功与否
     */
    public boolean ncm2Mp3(String ncmFilePath, String outFilePath) {
        return ncm2Mp3(ncmFilePath, outFilePath, ConvertOptions.defaults());
    }

    /**
     * NCM转换MP3
     * 功能:将NCM音乐转换为MP3,并按指定的标签模式写入 标题/歌手/专辑/封面
     *
     * @param ncmFilePath NCM文件路径
     * @param outFilePath MP3文件路径
     * @param tagMode     标签信息来源模式
     * @return 转换成功与否
     */
    public boolean ncm2Mp3(String ncmFilePath, String outFilePath, TagMode tagMode) {
        return ncm2Mp3(ncmFilePath, outFilePath, new ConvertOptions(tagMode, false));
    }

    /**
     * NCM转换MP3
     * 功能:将NCM音乐转换为MP3,支持高级配置选项
     *
     * @param ncmFilePath NCM文件路径
     * @param outFilePath MP3文件路径
     * @param options     转换配置选项
     * @return 转换成功与否
     */
    public boolean ncm2Mp3(String ncmFilePath, String outFilePath, ConvertOptions options) {
        try {
            Ncm ncm = new Ncm();
            ncm.setNcmFile(ncmFilePath);
            FileInputStream inputStream = new FileInputStream(ncm.getNcmFile());
            magicHeader(inputStream);
            byte[] key = cr4Key(inputStream);
            String mataJson = mataData(inputStream);
            Mata mata = JSON.parseObject(mataJson, Mata.class);
            ncm.setMata(mata);
            String musicId = mata.musicId;
            byte[] image = albumImage(inputStream);
            ncm.setImage(image);
            File ncmFile = new File(ncmFilePath);
            String baseName = ncmFile.getName().substring(0, ncmFile.getName().length() - 3);
            // 先用 NCM 元数据的格式作为临时扩展名，后续根据实际内容修正
            outFilePath += File.separator + baseName + ncm.getMata().format;
            ncm.setOutFile(outFilePath);
            FileOutputStream outputStream = new FileOutputStream(ncm.getOutFile());
            musicData(inputStream, outputStream, key);
            // 解密出的原始音频自带完整标签,转换前先全部取出,转换完成后再写回目标文件
            TagInfo sourceTag = collectSourceTag(ncmFile, mata, musicId, image, options.tagMode, new File(ncm.getOutFile()));
            if (options.reEncodeWithFfmpeg) {
                if (!isFfmpegAvailable()) {
                    System.out.format("警告：ffmpeg 未安装或不可用，跳过重编码。\n");
                } else {
                    reEncodeWithFfmpeg(ncm.getOutFile());
                    // ffmpeg 输出一定是 MP3，修正扩展名，否则 jaudiotagger 会按旧扩展名选错 reader
                    String mp3Path = replaceExtension(ncm.getOutFile(), "mp3");
                    if (!mp3Path.equals(ncm.getOutFile())) {
                        File oldFile = new File(ncm.getOutFile());
                        File newFile = new File(mp3Path);
                        if (oldFile.renameTo(newFile)) {
                            ncm.setOutFile(mp3Path);
                        }
                    }
                }
            }
            combineFile(ncm, sourceTag);
            // 未开启 ffmpeg 时，根据实际内容修正扩展名
            if (!options.reEncodeWithFfmpeg) {
                String realExt = detectActualFormat(ncm.getOutFile());
                File outFile = new File(ncm.getOutFile());
                String currentName = outFile.getName();
                int dotIdx = currentName.lastIndexOf('.');
                if (dotIdx > 0) {
                    String currentExt = currentName.substring(dotIdx + 1);
                    if (!currentExt.equalsIgnoreCase(realExt)) {
                        File renamed = new File(outFile.getParent(), currentName.substring(0, dotIdx) + "." + realExt);
                        if (outFile.renameTo(renamed)) {
                            ncm.setOutFile(renamed.getAbsolutePath());
                        }
                    }
                }
            }
            System.out.format("转换成功文件：%s\n", ncm.getOutFile());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.format("转换失败文件：%s\n", outFilePath);
            return false;
        }
    }

    /**
     * 按标签模式选择标签信息来源策略
     */
    private TagProvider tagProviderFor(TagMode tagMode) {
        return (tagMode == TagMode.PATH) ? new PathTagProvider() : new NcmTagProvider();
    }

    /**
     * 汇总一首歌的全部源信息:模式来源(NCM内置元数据/目录结构)为准,
     * 解密音频内的标签与同目录 .lrc 歌词补齐缺失字段
     *
     * @param ncmFile     NCM文件
     * @param mata        NCM内置元数据
     * @param musicId     音乐ID
     * @param ncmCover    NCM内置封面
     * @param tagMode     标签模式
     * @param decodedFile 解密出的原始音频
     * @return 待写回的标签信息
     */
    private TagInfo collectSourceTag(File ncmFile, Mata mata, String musicId, byte[] ncmCover,
                                     TagMode tagMode, File decodedFile) {
        TagInfo info = tagProviderFor(tagMode).provide(ncmFile, mata, musicId, ncmCover);
        info.fillBlanksFrom(LrcReader.read(ncmFile));
        info.fillBlanksFrom(AudioTagExtractor.extract(decodedFile));
        return info;
    }

    /**
     * NCM格式头部读取
     * 功能:MagicHeader读取
     *
     * @param inputStream ncm文件输入流
     */
    private void magicHeader(FileInputStream inputStream) throws Exception {
        Utils.readBlock(inputStream, 10);
    }

    /**
     * 获取CR4密钥
     * 功能:将用AES128加密的CR4密钥进行解密
     *
     * @param inputStream ncm文件输入流
     * @return CR4密钥
     */
    private byte[] cr4Key(FileInputStream inputStream) throws Exception {
        byte[] bytes = Utils.readBlock(inputStream, Utils.readLength(inputStream));
        //1.按字节对0x64异或
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= 0x64;
        }
        //2.AES解密(其中PKCS5Padding填充模式会去除末尾填充部分)
        bytes = AES.decrypt(bytes, AES.CORE_KEY, AES.TRANSFORMATION, AES.ALGORITHM);
        //3.去除前面`neteasecloudmusic`的17个字节
        byte[] key = new byte[bytes.length - 17];
        System.arraycopy(bytes, 17, key, 0, key.length);
        return key;
    }

    /**
     * 获取Mata头部信息
     * 功能:获取音乐的Mata头部信息,以JSON格式表示
     *
     * @param inputStream ncm文件输入流
     * @return JSON格式头部信息
     */
    private String mataData(FileInputStream inputStream) throws Exception {
        int len = Utils.readLength(inputStream);
        byte[] bytes = Utils.readBlock(inputStream, len);
        //跳过:CRC(4字节),unused Gap(5字节)
        Utils.skipBlock(inputStream, 9);
        //1.按字节对0x63异或
        for (int i = 0; i < len; i++) {
            bytes[i] ^= 0x63;
        }
        //2.去除前面`163 key(Don't modify):`22个字节
        byte[] temp = new byte[bytes.length - 22];
        System.arraycopy(bytes, 22, temp, 0, temp.length);
        //3.Base64进行decode转码
        temp = Base64.getDecoder().decode(temp);
        //4.AES解密(其中PKCS5Padding填充模式会去除末尾填充部分)
        temp = AES.decrypt(temp, AES.MATA_KEY, AES.TRANSFORMATION, AES.ALGORITHM);
        //5.去除前面`music:`6个字节后获得JSON
        return new String(temp, 6, temp.length - 6, StandardCharsets.UTF_8);
    }

    /**
     * 专辑图片
     * 功能:获取专辑图片数据
     *
     * @param inputStream ncm文件输入流
     * @return 专辑图片数据
     */
    private byte[] albumImage(FileInputStream inputStream) throws Exception {
        return Utils.readBlock(inputStream, Utils.readLength(inputStream));
    }

    /**
     * 音乐数据
     * 功能:获取音乐数据
     *
     * @param inputStream  ncm文件输入流
     * @param outputStream 存音乐数据的文件输出流
     * @param cr4Key       CR4密钥
     */
    private void musicData(FileInputStream inputStream, FileOutputStream outputStream, byte[] cr4Key) throws Exception {
        CR4 cr4 = new CR4();
        cr4.KSA(cr4Key);
        byte[] buffer = new byte[0x8000];
        for (int len; (len = inputStream.read(buffer)) > 0; ) {
            cr4.PRGA(buffer, len);
            outputStream.write(buffer, 0, len);
        }
        inputStream.close();
        outputStream.close();
    }

    /**
     * 功能:将源信息(标题/艺人/专辑/年份/流派/音轨/作曲/作词/歌词/封面)写回转换后的音频文件
     *
     */
    private void combineFile(Ncm ncm, TagInfo tagInfo) throws Exception{
        AudioFile audioFile;
        try {
            audioFile = AudioFileIO.read(new File(ncm.getOutFile()));
        } catch (Exception e) {
            // 解密出的原始音频数据可能不含合法的音频帧头，跳过标签写入。
            System.out.format("跳过标签写入（音频格式无法识别）：%s\n", ncm.getOutFile());
            return;
        }
        Tag tag = audioFile.getTagOrCreateDefault();
        if (tag != null) {
            audioFile.setTag(tag);
            putField(ncm, tag, FieldKey.TITLE, tagInfo.title);
            putField(ncm, tag, FieldKey.ARTIST, tagInfo.joinedArtists());
            putField(ncm, tag, FieldKey.ALBUM, tagInfo.album);
            putField(ncm, tag, FieldKey.ALBUM_ARTIST, tagInfo.albumArtist);
            putField(ncm, tag, FieldKey.YEAR, leadingDigits(tagInfo.year));
            putField(ncm, tag, FieldKey.GENRE, tagInfo.genre);
            putField(ncm, tag, FieldKey.TRACK, leadingDigits(tagInfo.track));
            putField(ncm, tag, FieldKey.DISC_NO, leadingDigits(tagInfo.disc));
            putField(ncm, tag, FieldKey.COMPOSER, tagInfo.composer);
            putField(ncm, tag, FieldKey.LYRICIST, tagInfo.lyricist);
            putField(ncm, tag, FieldKey.COMMENT, tagInfo.comment);
            putField(ncm, tag, FieldKey.LYRICS, tagInfo.lyrics);
            if (tagInfo.cover != null && tagInfo.cover.length > 0) {
                try {
                    Artwork artwork = ArtworkFactory.getNew();
                    artwork.setBinaryData(tagInfo.cover);
                    artwork.setMimeType(Utils.albumImageMimeType(tagInfo.cover));
                    artwork.setPictureType(3);  // 3 = front cover, iPod 只认这个
                    artwork.setDescription("");
                    tag.setField(artwork);
                } catch (Exception e) {
                    System.out.format("跳过封面写入：%s (%s)\n", ncm.getOutFile(), e.getMessage());
                }
            }
            try {
                AudioFileIO.write(audioFile);
            } catch (Exception e) {
                System.out.format("跳过标签写入（jaudiotagger write 失败）：%s (%s)\n", ncm.getOutFile(), e.getMessage());
            }
        } else {
            System.out.format("跳过标签写入（无法创建 Tag）：%s\n", ncm.getOutFile());
        }
    }

    /**
     * 写入单个标签字段:空值跳过,格式不支持时只跳过该字段而不影响其他字段
     */
    private void putField(Ncm ncm, Tag tag, FieldKey key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        try {
            tag.setField(key, value);
        } catch (Exception e) {
            System.out.format("字段写入跳过 %s：%s (%s)\n", key, ncm.getOutFile(), e.getMessage());
        }
    }

    /**
     * 只保留开头的数字,用于 年份/音轨号/碟片号 这类要求纯数字的字段(如 "1/12" -> "1")
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

    /**
     * 探测输出文件的实际音频格式，返回标准扩展名
     */
    private static String replaceExtension(String path, String newExt) {
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx > 0) {
            return path.substring(0, dotIdx) + "." + newExt;
        }
        return path + "." + newExt;
    }

    private String detectActualFormat(String filePath) {
        try {
            byte[] header = new byte[16];
            try (FileInputStream fis = new FileInputStream(filePath)) {
                int read = fis.read(header);
                if (read < 4) return "mp3";
            }
            // ID3 标签开头 → MP3
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') return "mp3";
            // MPEG sync word (0xFF 0xFB / 0xFF 0xF3 / 0xFF 0xFA / 0xFF 0xF2)
            if ((header[0] & 0xFF) == 0xFF && ((header[1] & 0xE0) == 0xE0)) return "mp3";
            // FLAC magic "fLaC"
            if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') return "flac";
            // OGG magic "OggS"
            if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') return "ogg";
            // WAV magic "RIFF"
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') return "wav";
            // MP4/AAC magic (ftyp box, offsets vary)
            if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') return "m4a";
            // fallback: 尝试用 ffprobe 探测
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffprobe", "-v", "quiet",
                        "-show_entries", "stream=codec_name",
                        "-of", "default=noprint_wrappers=1:nokey=1",
                        filePath
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder out = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) out.append(l).append(" ");
                }
                p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                String probed = out.toString();
                if (probed.contains("flac")) return "flac";
                if (probed.contains("mp3")) return "mp3";
                if (probed.contains("aac")) return "m4a";
                if (probed.contains("vorbis")) return "ogg";
            } catch (Exception ignored) {}
        } catch (Exception e) {
            // ignore
        }
        return "mp3";
    }

    /**
     * 检查 ffmpeg 是否可用
     */
    private static boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用 ffmpeg 将解密后的音频重新编码为标准 MP3 (320kbps CBR)
     */
    private void reEncodeWithFfmpeg(String filePath) {
        File original = new File(filePath);
        File temp = new File(filePath + ".ffmpeg.mp3");
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", "pipe:0",
                    "-codec:a", "libmp3lame",
                    "-b:a", "320k",
                    "-map_metadata", "-1",
                    temp.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            p = pb.start();
            // 分块写入标准输入:整首音频进堆会在并发转换时耗尽内存
            try (FileInputStream fis = new FileInputStream(original);
                 OutputStream os = p.getOutputStream()) {
                byte[] buffer = new byte[0x8000];
                for (int len; (len = fis.read(buffer)) > 0; ) {
                    os.write(buffer, 0, len);
                }
            }
            // 静默读取避免阻塞，不打印错误
            try (java.io.InputStream is = p.getInputStream()) {
                is.transferTo(java.io.OutputStream.nullOutputStream());
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && isValidAudio(temp)) {
                if (original.delete()) {
                    temp.renameTo(original);
                } else {
                    temp.delete();
                }
                System.out.format("ffmpeg 重编码完成：%s\n", filePath);
            } else {
                temp.delete();
                System.out.format("ffmpeg 重编码失败（无法解析该文件），保留原始文件：%s\n", filePath);
            }
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            temp.delete();
            System.out.format("ffmpeg 重编码失败，保留原始文件：%s\n", filePath);
        }
    }

    /** 验证音频文件是否合法（有有效时长） */
    private boolean isValidAudio(File file) {
        if (!file.exists() || file.length() < 1024) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                line = reader.readLine();
            }
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (line != null && !line.equals("N/A") && !line.isEmpty()) {
                return Double.parseDouble(line) > 0.5;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

}
