package com.johnny.flac;

/**
 * 输出目录里的文件摆放方式
 *
 * @author johnny
 */
public enum OutputLayout {

    /**
     * 全部 MP3 直接放在输出目录顶层
     */
    FLAT("平铺(全部放在输出目录顶层)"),

    /**
     * 按扫描根目录的相对路径重建层级
     */
    KEEP_TREE("保持原目录层级");

    private final String label;

    OutputLayout(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 解析持久化值,认不出来时退回默认值而不抛异常
     */
    public static OutputLayout from(String value, OutputLayout fallback) {
        for (OutputLayout layout : values()) {
            if (layout.name().equalsIgnoreCase(value)) {
                return layout;
            }
        }
        return fallback;
    }
}
