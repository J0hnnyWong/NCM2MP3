package com.johnny.flac;

import java.io.File;
import java.nio.file.Path;

/**
 * 列表一行对应的源文件:绝对路径 + 相对扫描根目录的路径(表格里展示的就是后者)
 *
 * @author johnny
 */
public class SourceItem {

    private final File source;

    private final String relativePath;

    public SourceItem(File source, String relativePath) {
        this.source = source;
        this.relativePath = relativePath;
    }

    public File source() {
        return source;
    }

    /**
     * 展示与去重用的相对路径
     */
    public String display() {
        return relativePath;
    }

    /**
     * 保持层级时的输出路径:相对路径原样,扩展名换成 .mp3
     */
    public String treeTarget() {
        return replaceExtension(relativePath, "mp3");
    }

    /**
     * 平铺时的默认输出名:只取文件名,扩展名换成 .mp3(重名时由 OutputPlanner 再加区分)
     */
    public String flatTarget() {
        return replaceExtension(source.getName(), "mp3");
    }

    /**
     * 相对路径从内到外的各级目录名,平铺重名时用来把专辑/歌手拼进文件名
     */
    public String[] ancestors() {
        String[] parts = relativePath.replace('\\', '/').split("/");
        // 最后一段是文件名,其余是目录
        String[] dirs = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 0, dirs, 0, dirs.length);
        return dirs;
    }

    private static String replaceExtension(String name, String newExtension) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + "." + newExtension : name + "." + newExtension;
    }
}
