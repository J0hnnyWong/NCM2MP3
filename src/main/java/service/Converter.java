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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Paths;

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
            String musicId = JSON.parseObject(mataJson).getString("musicId");
            byte[] image = albumImage(inputStream);
            ncm.setImage(image);
            File ncmFile = new File(ncmFilePath);
            String baseName = ncmFile.getName().substring(0, ncmFile.getName().length() - 3);
            // 先用 NCM 元数据的格式作为临时扩展名，后续根据实际内容修正
            outFilePath += File.separator + baseName + ncm.getMata().format;
            ncm.setOutFile(outFilePath);
            FileOutputStream outputStream = new FileOutputStream(ncm.getOutFile());
            musicData(inputStream, outputStream, key);
            if (options.reEncodeWithFfmpeg) {
                if (!isFfmpegAvailable()) {
                    System.out.format("警告：ffmpeg 未安装或不可用，跳过重编码。请执行 brew install ffmpeg 安装。\n");
                } else {
                    reEncodeWithFfmpeg(ncm.getOutFile());
                }
            }
            combineFile(ncm, tagProviderFor(options.tagMode).provide(ncmFile, mata, musicId, image));
            // 根据实际文件内容修正扩展名
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
                        outFilePath = renamed.getAbsolutePath();
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
     * NCM格式头部读取
     * 功能:MagicHeader读取
     *
     * @param inputStream ncm文件输入流
     */
    private void magicHeader(FileInputStream inputStream) throws Exception {
        byte[] bytes = new byte[10];
        inputStream.read(bytes, 0, 10);
    }

    /**
     * 获取CR4密钥
     * 功能:将用AES128加密的CR4密钥进行解密
     *
     * @param inputStream ncm文件输入流
     * @return CR4密钥
     */
    private byte[] cr4Key(FileInputStream inputStream) throws Exception {
        byte[] bytes = new byte[4];
        inputStream.read(bytes, 0, 4);
        int len = Utils.getLength(bytes);
        bytes = new byte[len];
        inputStream.read(bytes, 0, len);
        //1.按字节对0x64异或
        for (int i = 0; i < len; i++) {
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
        byte[] bytes = new byte[4];
        inputStream.read(bytes, 0, 4);
        int len = Utils.getLength(bytes);
        bytes = new byte[len];
        inputStream.read(bytes, 0, len);
        //跳过:CRC(4字节),unused Gap(5字节)
        inputStream.skip(9);
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
        byte[] bytes = new byte[4];
        inputStream.read(bytes, 0, 4);
        int len = Utils.getLength(bytes);
        byte[] imageData = new byte[len];
        inputStream.read(imageData, 0, len);
        return imageData;
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
     * 功能:将NCM中各个信息整合到一起,转换成对应音乐格式
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
            tag.setField(FieldKey.ALBUM, tagInfo.album);
            tag.setField(FieldKey.TITLE, tagInfo.title);
            tag.setField(FieldKey.ARTIST, tagInfo.artists);
            if (tagInfo.cover != null && tagInfo.cover.length > 0) {
                try {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(tagInfo.cover));
                    if (image != null) {
                        MetadataBlockDataPicture coverArt = new MetadataBlockDataPicture(
                                tagInfo.cover, 0, Utils.albumImageMimeType(tagInfo.cover),
                                "", image.getWidth(), image.getHeight(),
                                image.getColorModel().hasAlpha() ? 32 : 24, 0);
                        Artwork artwork = ArtworkFactory.createArtworkFromMetadataBlockDataPicture(coverArt);
                        tag.setField(tag.createField(artwork));
                    }
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
     * 探测输出文件的实际音频格式，返回标准扩展名
     */
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
            // fallback to ffprobe
            byte[] rawData = Files.readAllBytes(Paths.get(filePath));
            String probed = probeAudioFormat(rawData);
            if (probed.contains("flac")) return "flac";
            if (probed.contains("mp3")) return "mp3";
            if (probed.contains("aac")) return "m4a";
            if (probed.contains("vorbis")) return "ogg";
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
            // 先读取原始文件全部字节
            byte[] rawData = java.nio.file.Files.readAllBytes(original.toPath());

            // 先通过管道方式用 ffprobe 探测实际编码格式
            String probedFormat = probeAudioFormat(rawData);
            System.out.format("[ffmpeg] 探测到音频格式: %s (文件: %s)\n", probedFormat, filePath);

            // 用管道方式重编码：数据从 stdin 传入，ffmpeg 无视扩展名自行探测
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

            // 写入原始数据到 ffmpeg stdin
            p.getOutputStream().write(rawData);
            p.getOutputStream().close();

            // 读取 ffmpeg 输出用于调试
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("error")) {
                        System.out.format("[ffmpeg] %s\n", line);
                    }
                }
            }

            int exitCode = p.waitFor();
            if (exitCode != 0) {
                temp.delete();
                System.out.format("ffmpeg 重编码失败（退出码: %d），保留原始文件：%s\n", exitCode, filePath);
                return;
            }

            if (!original.delete()) {
                temp.delete();
                System.out.format("ffmpeg 重编码失败（无法删除原始文件），保留原始文件：%s\n", filePath);
                return;
            }
            if (!temp.renameTo(original)) {
                System.out.format("ffmpeg 重编码失败（无法重命名临时文件），保留原始文件：%s\n", filePath);
                return;
            }
            System.out.format("ffmpeg 重编码完成：%s\n", filePath);
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            temp.delete();
            System.out.format("ffmpeg 重编码异常（%s），保留原始文件：%s\n", e.getMessage(), filePath);
        }
    }

    /**
     * 用 ffprobe 探测音频的实际编码格式（写入无扩展名临时文件以正确探测）
     */
    private String probeAudioFormat(byte[] rawData) {
        File probeFile = null;
        try {
            probeFile = File.createTempFile("ncm_probe_", null);
            java.nio.file.Files.write(probeFile.toPath(), rawData);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "quiet",
                    "-i", probeFile.getAbsolutePath(),
                    "-show_entries", "stream=codec_name",
                    "-of", "default=noprint_wrappers=1:nokey=1"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(" ");
                }
            }
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            String result = output.toString().trim();
            return result.isEmpty() ? "unknown" : result;
        } catch (Exception e) {
            return "unknown";
        } finally {
            if (probeFile != null) probeFile.delete();
        }
    }
}
