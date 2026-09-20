package service.tag;

import mime.Mata;

import java.io.File;

/**
 * 默认模式:标签信息全部来自NCM内置元数据(保持原有行为不变)
 *
 * @author charlottexiao
 */
public class NcmTagProvider implements TagProvider {

    @Override
    public TagInfo provide(File ncmFile, Mata mata, String musicId, byte[] ncmCover) {
        TagInfo info = new TagInfo();
        info.title = mata.musicName;
        info.artists = ArtistNames.of(mata.artist);
        info.album = mata.album;
        if (info.artists.length > 0) {
            info.albumArtist = info.artists[0];
        }
        info.cover = ncmCover;
        return info;
    }
}
