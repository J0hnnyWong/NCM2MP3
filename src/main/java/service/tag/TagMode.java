package service.tag;

/**
 * 标签信息来源模式
 *
 * @author charlottexiao
 */
public enum TagMode {

    /**
     * 默认模式:使用NCM内置元数据(标题/歌手/专辑/封面均来自NCM文件)
     */
    NCM("NCM内置信息(默认)"),

    /**
     * 路径模式:歌手/专辑按 歌手/专辑/歌曲 目录结构获取,封面取 meta/track-{musicId} 图片
     */
    PATH("目录结构+meta封面");

    private final String displayName;

    TagMode(String displayName) {
        this.displayName = displayName;
    }

    public static TagMode from(String name) {
        if (name != null) {
            for (TagMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            System.out.printf("未知的标签模式: %s, 使用默认模式(ncm)%n", name);
        }
        return NCM;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
