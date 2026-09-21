package com.johnny.flac;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

/**
 * FLAC 转 MP3 并回写元信息的小工具界面:
 * 选输入目录递归扫描 FLAC → 选输出目录 → 开始转换,列表可展示与清空
 *
 * @author johnny
 */
public class FlacConverterFrame extends JFrame {

    private static final Preferences PREFS = Preferences.userNodeForPackage(FlacConverterFrame.class);
    private static final String PREF_INPUT = "inputDir";
    private static final String PREF_OUTPUT = "outputDir";

    private static final String STATUS_WAITING = "待转换";
    private static final String STATUS_RUNNING = "转换中";

    /**
     * 与表格模型行一一对应,只在事件分发线程上增删
     */
    private final List<Item> items = new ArrayList<>();

    private final Set<String> listedPaths = ConcurrentHashMap.newKeySet();

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();

    private final ExecutorService convertExecutor = Executors.newFixedThreadPool(convertThreads());

    private JTextField inputField;
    private JTextField outputField;
    private DefaultTableModel tableModel;
    private JTable table;
    private JButton scanButton;
    private JButton convertButton;
    private JButton clearButton;
    private JProgressBar progressBar;
    private JLabel infoLabel;
    private volatile boolean ffmpegAvailable;

    public FlacConverterFrame() {
        super("FLAC 元信息转换");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initComponents();
        inputField.setText(PREFS.get(PREF_INPUT, ""));
        outputField.setText(PREFS.get(PREF_OUTPUT, ""));
        checkFfmpeg();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        Container content = getContentPane();
        content.setLayout(new BorderLayout(8, 8));
        ((JComponent) content).setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        content.add(northPanel(), BorderLayout.NORTH);
        content.add(centerPanel(), BorderLayout.CENTER);
        content.add(southPanel(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(940, 560));
        pack();
    }

    private JPanel northPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        inputRow.add(new JLabel("FLAC 目录:"));
        inputField = new JTextField(30);
        inputRow.add(inputField);
        JButton pickInput = new JButton("选择目录");
        pickInput.addActionListener(e -> chooseDirectory(inputField, "请选择包含 FLAC 的目录"));
        inputRow.add(pickInput);
        scanButton = new JButton("扫描");
        scanButton.addActionListener(e -> scan());
        inputRow.add(scanButton);
        panel.add(inputRow);

        JPanel outputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        outputRow.add(new JLabel("输出目录:"));
        outputField = new JTextField(30);
        outputRow.add(outputField);
        JButton pickOutput = new JButton("选择目录");
        pickOutput.addActionListener(e -> chooseDirectory(outputField, "请选择转换结果保存目录"));
        outputRow.add(pickOutput);
        outputRow.add(Box.createHorizontalStrut(8));
        outputRow.add(new JLabel("保持原目录层级,输出同名 .mp3"));
        panel.add(outputRow);
        return panel;
    }

    private JScrollPane centerPanel() {
        tableModel = new DefaultTableModel(
                new Object[][]{},
                new String[]{"文件", "艺人", "专辑", "标题", "状态"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(0).setPreferredWidth(280);
        columns.getColumn(1).setPreferredWidth(130);
        columns.getColumn(2).setPreferredWidth(150);
        columns.getColumn(3).setPreferredWidth(150);
        columns.getColumn(4).setPreferredWidth(150);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(900, 380));
        return scrollPane;
    }

    private JPanel southPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        convertButton = new JButton("开始转换");
        convertButton.addActionListener(e -> convertAll());
        buttons.add(convertButton);
        clearButton = new JButton("清空列表");
        clearButton.addActionListener(e -> clearList());
        buttons.add(clearButton);
        panel.add(buttons, BorderLayout.WEST);

        progressBar = new JProgressBar(0, 0);
        progressBar.setStringPainted(true);
        panel.add(progressBar, BorderLayout.CENTER);

        infoLabel = new JLabel("就绪", SwingConstants.LEFT);
        panel.add(infoLabel, BorderLayout.EAST);
        return panel;
    }

    /**
     * 递归收集 .flac(按文件头确认是真 FLAC,改名过的别的格式不算)并读取标签填表;
     * 标签读取在后台线程做,界面不卡
     */
    private void scan() {
        File root = new File(inputField.getText().trim());
        if (!root.isDirectory()) {
            infoLabel.setText("请先选择存在的 FLAC 目录");
            return;
        }
        setBusy(true);
        infoLabel.setText("正在扫描...");
        scanExecutor.submit(() -> {
            List<File> found = new ArrayList<>();
            collect(root, found);
            int added = 0;
            int notFlac = 0;
            for (File file : found) {
                String path = file.getAbsolutePath();
                if (listedPaths.contains(path)) {
                    continue;
                }
                if (!SourceReader.isFlac(file)) {
                    notFlac++;
                    continue;
                }
                listedPaths.add(path);
                Item item = new Item(file, relativePath(root, file));
                Metadata data = SourceReader.summary(file);
                appendRow(item, data);
                added++;
            }
            int count = added;
            int skipped = notFlac;
            SwingUtilities.invokeLater(() -> {
                setBusy(false);
                infoLabel.setText("本次新增 " + count + " 个,列表共 " + items.size() + " 个"
                        + (skipped == 0 ? "" : ",跳过 " + skipped + " 个非 FLAC 文件"));
            });
        });
    }

    private void collect(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, result);
            } else if (child.getName().toLowerCase().endsWith(".flac")) {
                result.add(child);
            }
        }
    }

    /**
     * 逐个转换列表里"待转换"的条目
     */
    private void convertAll() {
        String output = outputField.getText().trim();
        if (output.isEmpty()) {
            infoLabel.setText("请先选择输出目录");
            return;
        }
        if (!ffmpegAvailable) {
            infoLabel.setText("未检测到 ffmpeg,无法转码(Windows: winget install Gyan.FFmpeg)");
            return;
        }
        PREFS.put(PREF_INPUT, inputField.getText().trim());
        PREFS.put(PREF_OUTPUT, output);

        final List<Integer> pending = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (STATUS_WAITING.equals(tableModel.getValueAt(row, 4))) {
                pending.add(row);
            }
        }
        if (pending.isEmpty()) {
            infoLabel.setText("没有待转换的条目");
            return;
        }
        setBusy(true);
        File outputRoot = new File(output);
        progressBar.setMaximum(pending.size());
        progressBar.setValue(0);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        for (int row : pending) {
            tableModel.setValueAt(STATUS_RUNNING, row, 4);
            Item item = items.get(row);
            File target = item.targetIn(outputRoot);
            convertExecutor.submit(() -> {
                String status;
                boolean isError = false;
                try {
                    status = Converter.convert(item.source, target).statusText();
                } catch (Exception e) {
                    status = "失败: " + reasonOf(e);
                    isError = true;
                }
                final String text = status;
                final boolean error = isError;
                SwingUtilities.invokeLater(() -> {
                    tableModel.setValueAt(text, row, 4);
                    progressBar.setValue(done.incrementAndGet());
                    if (error) {
                        failed.incrementAndGet();
                    }
                    if (done.get() == pending.size()) {
                        setBusy(false);
                        infoLabel.setText("完成 " + done.get() + " 个,失败 " + failed.get() + " 个");
                    }
                });
            });
        }
    }

    private void clearList() {
        tableModel.setRowCount(0);
        items.clear();
        listedPaths.clear();
        progressBar.setMaximum(0);
        progressBar.setValue(0);
        infoLabel.setText("列表已清空");
    }

    private void appendRow(Item item, Metadata data) {
        SwingUtilities.invokeLater(() -> {
            items.add(item);
            tableModel.addRow(new Object[]{
                    item.display,
                    data.artistText(),
                    data.album,
                    data.title,
                    STATUS_WAITING
            });
        });
    }

    /**
     * 后台探测 ffmpeg,避免界面等子进程
     */
    private void checkFfmpeg() {
        scanExecutor.submit(() -> {
            boolean ok = Mp3Encoder.available();
            SwingUtilities.invokeLater(() -> {
                ffmpegAvailable = ok;
                convertButton.setEnabled(ok);
                if (ok) {
                    infoLabel.setText("ffmpeg 可用,输出 MP3 " + Mp3Encoder.BITRATE + " CBR");
                } else {
                    infoLabel.setText("未检测到 ffmpeg,安装后重启本程序(Windows: winget install Gyan.FFmpeg)");
                }
            });
        });
    }

    private void chooseDirectory(JTextField field, String title) {
        JFileChooser chooser = new JFileChooser(field.getText().trim());
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void setBusy(boolean busy) {
        scanButton.setEnabled(!busy);
        convertButton.setEnabled(!busy && ffmpegAvailable);
        clearButton.setEnabled(!busy);
        progressBar.setIndeterminate(busy && progressBar.getMaximum() == 0);
    }

    private static String relativePath(File root, File file) {
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        Path filePath = file.toPath().toAbsolutePath().normalize();
        try {
            return rootPath.relativize(filePath).toString();
        } catch (IllegalArgumentException e) {
            return file.getName();
        }
    }

    private static String reasonOf(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private static int convertThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(4, cores / 2));
    }

    /**
     * 列表一行的来源信息:绝对路径 + 相对扫描根目录的展示路径
     */
    private static class Item {

        private final File source;

        private final String display;

        Item(File source, String display) {
            this.source = source;
            this.display = display;
        }

        /**
         * 输出目录里保持与源目录相同的层级,扩展名换成 .mp3
         */
        File targetIn(File outputRoot) {
            String name = display;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot) + ".mp3";
            } else {
                name = name + ".mp3";
            }
            return outputRoot.toPath().resolve(name).toFile();
        }
    }
}
