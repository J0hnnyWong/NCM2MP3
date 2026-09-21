package com.johnny.flac;

import com.formdev.flatlaf.FlatIntelliJLaf;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author johnny
 */
public class Main {

    public static void main(String[] args) {
        // jaudiotagger 默认会为每个文件打一串解析日志,从命令行启动时刷屏,这里静音到只报严重错误
        Logger.getLogger("org.jaudiotagger").setLevel(Level.SEVERE);
        FlatIntelliJLaf.setup();
        SwingUtilities.invokeLater(() -> new FlacConverterFrame().setVisible(true));
    }
}
