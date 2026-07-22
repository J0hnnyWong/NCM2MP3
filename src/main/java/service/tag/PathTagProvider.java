package service.tag;

import mime.Mata;

import java.io.File;
import java.nio.file.Files;

/**
 * 路径模式:按 歌手/专辑/歌曲 目录结构获取歌手与专辑,
 * 封面从上级 meta 文件夹中取 track-{musicId} 图片,找不到时回退到NCM内置封面
 *
 * @author charlottexiao
 */
public class PathTagProvider implements TagProvider {

    private static final String[] COVER_EXTENSIONS = {".jpg", ".jpeg", ".png"};

    @Override
    public TagInfo provide(File ncmFile, Mata mata, String musicId, byte[] ncmCover) {
        TagInfo info = new TagInfo();
        info.title = mata.musicName;

        File albumDir = ncmFile.getParentFile();
        File artistDir = (albumDir != null) ? albumDir.getParentFile() : null;
        info.album = (albumDir != null) ? albumDir.getName() : mata.album;
        info.artists = new String[]{(artistDir != null) ? artistDir.getName() : firstArtist(mata)};
        info.cover = loadCover(albumDir, musicId, ncmCover);
        return info;
    }

    private String firstArtist(Mata mata) {
        if (mata.artist != null && mata.artist.length > 0 && mata.artist[0].length > 0) {
            return mata.artist[0][0];
        }
        return "";
    }

    private byte[] loadCover(File startDir, String musicId, byte[] ncmCover) {
        File metaDir = findMetaDir(startDir);
        if (metaDir != null && musicId != null && !musicId.isEmpty()) {
            for (String ext : COVER_EXTENSIONS) {
                File coverFile = new File(metaDir, "track-" + musicId + ext);
                if (coverFile.isFile()) {
                    try {
                        return Files.readAllBytes(coverFile.toPath());
                    } catch (Exception e) {
                        System.out.printf("封面读取失败: %s, 使用NCM内置封面%n", coverFile.getAbsolutePath());
                    }
                }
            }
        }
        return ncmCover;
    }

    /**
     * 从歌曲所在目录逐级向上寻找包含meta文件夹的目录
     */
    private File findMetaDir(File dir) {
        while (dir != null) {
            File meta = new File(dir, "meta");
            if (meta.isDirectory()) {
                return meta;
            }
            dir = dir.getParentFile();
        }
        return null;
    }
}
