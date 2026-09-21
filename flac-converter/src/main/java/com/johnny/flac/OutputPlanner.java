package com.johnny.flac;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 规划每个源文件落在输出目录里的哪一个文件名。
 * 保持层级时相对路径天然唯一;平铺时不同专辑里的同名歌会互相覆盖,
 * 所以撞名的条目往上取目录名(通常是专辑名)拼进文件名区分,还区分不开才加序号。
 *
 * @author johnny
 */
public final class OutputPlanner {

    /**
     * 平铺撞名时最多往上取几级目录
     */
    private static final int MAX_ANCESTOR_DEPTH = 2;

    private OutputPlanner() {
    }

    /**
     * @param items      本次要转换的条目,顺序即返回列表的顺序
     * @param outputRoot 用户选的输出目录
     * @param layout     摆放方式
     * @return 与 items 一一对应的目标文件
     */
    public static List<File> plan(List<SourceItem> items, File outputRoot, OutputLayout layout) {
        List<File> targets = new ArrayList<>(items.size());
        if (layout == OutputLayout.KEEP_TREE) {
            for (SourceItem item : items) {
                targets.add(new File(outputRoot, item.treeTarget()));
            }
            return targets;
        }
        // Windows 文件名不区分大小写,判重统一按小写
        Set<String> used = new HashSet<>();
        for (SourceItem item : items) {
            String name = uniqueName(item, used);
            used.add(name.toLowerCase(Locale.ROOT));
            targets.add(new File(outputRoot, name));
        }
        return targets;
    }

    private static String uniqueName(SourceItem item, Set<String> used) {
        String flat = item.flatTarget();
        if (!used.contains(flat.toLowerCase(Locale.ROOT))) {
            return flat;
        }
        String[] ancestors = item.ancestors();
        for (int depth = 1; depth <= Math.min(MAX_ANCESTOR_DEPTH, ancestors.length); depth++) {
            String marker = tail(ancestors, depth);
            if (marker.isEmpty()) {
                continue;
            }
            String candidate = withMarker(flat, marker);
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        String base = stripExtension(flat);
        for (int index = 2; index < 1000; index++) {
            String candidate = base + " (" + index + ").mp3";
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return flat;
    }

    /**
     * 把区分信息插到扩展名前面
     */
    private static String withMarker(String name, String marker) {
        return stripExtension(name) + " [" + marker + "].mp3";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 取相对路径末尾若干级目录名,用 " - " 连起来
     */
    private static String tail(String[] ancestors, int depth) {
        StringBuilder builder = new StringBuilder();
        for (int i = Math.max(0, ancestors.length - depth); i < ancestors.length; i++) {
            if (builder.length() > 0) {
                builder.append(" - ");
            }
            builder.append(sanitize(ancestors[i]));
        }
        return builder.toString();
    }

    /**
     * 去掉文件名里的非法字符,目录名可能带 / \ : * ? 之类
     */
    private static String sanitize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            builder.append("<>:\"/\\|?*".indexOf(c) >= 0 ? ' ' : c);
        }
        return builder.toString().trim();
    }
}
