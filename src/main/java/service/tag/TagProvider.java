package service.tag;

import mime.Mata;

import java.io.File;

/**
 * 标签信息来源策略:不同模式从不同的地方获取 标题/歌手/专辑/封面
 *
 * @author charlottexiao
 */
public interface TagProvider {

    /**
     * 解析一首NCM音乐对应的标签信息
     *
     * @param ncmFile  NCM文件
     * @param mata     NCM内置元数据
     * @param musicId  NCM内置元数据中的musicId(对应meta/track-{musicId}封面命名)
     * @param ncmCover NCM内置封面数据
     * @return 标签信息
     */
    TagInfo provide(File ncmFile, Mata mata, String musicId, byte[] ncmCover);
}
