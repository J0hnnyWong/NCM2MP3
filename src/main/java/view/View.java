/*
 * Created by JFormDesigner on Sat Jan 23 11:01:06 CST 2021
 */

package view;

import com.formdev.flatlaf.FlatIntelliJLaf;
import executor.AsyncTaskExecutor;
import executor.ConvertTask;
import service.ConvertOptions;
import service.tag.TagMode;
import utils.Utils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

/**
 * @author charlottexiao
 */
public class View extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(View.class);
    private static final String PREF_TAG_MODE = "tagMode";
    private static final String PREF_FFMPEG = "reEncodeWithFfmpeg";
    private static final String PREF_KEEP_FLAC = "keepFlacWithMp3";

    /** 一批转换进行中时禁用"开始转换",避免重复提交同一行 */
    private volatile boolean converting;

    public View() {
        FlatIntelliJLaf.setup();
        loadPreferences();
        initComponents();
    }

    private void loadPreferences() {
        String savedTagMode = PREFS.get(PREF_TAG_MODE, TagMode.NCM.name());
        currentOptions.tagMode = TagMode.from(savedTagMode);
        currentOptions.reEncodeWithFfmpeg = PREFS.getBoolean(PREF_FFMPEG, false);
        currentOptions.keepFlacWithMp3 = PREFS.getBoolean(PREF_KEEP_FLAC, false);
    }

    private void savePreferences() {
        PREFS.put(PREF_TAG_MODE, currentOptions.tagMode.name());
        PREFS.putBoolean(PREF_FFMPEG, currentOptions.reEncodeWithFfmpeg);
        PREFS.putBoolean(PREF_KEEP_FLAC, currentOptions.keepFlacWithMp3);
    }

    private void button1MouseClicked(MouseEvent e) {
        int returnVal = jFileChooser1.showOpenDialog(panel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File[] files = jFileChooser1.getSelectedFiles();
            ArrayList<File> arrayList = new ArrayList<>();
            for (File file : files) {
                Utils.listAllFiles(arrayList, file);
            }
            for (File file : arrayList) {
                ((DefaultTableModel) table.getModel()).addRow(new String[]{file.getName(), file.getAbsolutePath(), String.valueOf(file.length()), "准备转换"});
            }
        }
    }

    private void button2MouseClicked(MouseEvent e) {
        if (converting) {
            return;
        }
        int returnVal = jFileChooser2.showOpenDialog(panel);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String outFilePath = jFileChooser2.getSelectedFile().getAbsolutePath();
        final TableModel model = table.getModel();
        List<Integer> pending = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            if ("准备转换".equals(model.getValueAt(i, 3))) {
                pending.add(i);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        converting = true;
        button2.setEnabled(false);
        final int total = pending.size();
        final AtomicInteger done = new AtomicInteger();
        List<Future<Boolean>> tasks = new ArrayList<>();
        for (Integer row : pending) {
            String ncmFilePath = (String) model.getValueAt(row, 1);
            tasks.add(AsyncTaskExecutor.submit(new ConvertTask(ncmFilePath, outFilePath, currentOptions, model, row, () -> {
                int finished = done.incrementAndGet();
                setTitle("NCM2MP3 - 已完成 " + finished + "/" + total);
                if (finished == total) {
                    setTitle("NCM2MP3");
                    converting = false;
                    button2.setEnabled(true);
                }
            })));
        }
        // 汇总打印放到普通线程做:占住转换线程池的线程只会让整批更慢
        Thread summary = new Thread(() -> Utils.waitForAllTask(tasks, result -> result), "convert-summary");
        summary.setDaemon(true);
        summary.start();
    }

    private void button3MouseClicked(MouseEvent e) {
        int rowCount = table.getModel().getRowCount();
        for (int i = 1; i <= rowCount; i++) {
            ((DefaultTableModel) table.getModel()).removeRow(rowCount - i);
        }
    }

    private void button4MouseClicked(MouseEvent e) {
        JDialog dialog = new JDialog(this, "高级配置", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));

        // --- 标签模式 ---
        JPanel tagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        tagPanel.add(new JLabel("标签信息来源："));
        JComboBox<TagMode> tagComboBox = new JComboBox<>(TagMode.values());
        tagComboBox.setSelectedItem(currentOptions.tagMode);
        tagPanel.add(tagComboBox);
        settingsPanel.add(tagPanel);

        settingsPanel.add(Box.createVerticalStrut(10));

        // --- ffmpeg 重编码 ---
        JPanel ffmpegPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JCheckBox ffmpegCheckbox = new JCheckBox("ffmpeg 重新编码为 MP3 (320kbps CBR)");
        ffmpegCheckbox.setSelected(currentOptions.reEncodeWithFfmpeg);

        // 检查 ffmpeg 是否可用
        boolean ffmpegOk = isFfmpegInstalled();
        if (!ffmpegOk) {
            ffmpegCheckbox.setEnabled(false);
            ffmpegCheckbox.setToolTipText("需要先把 ffmpeg 加入 PATH(Windows: winget install Gyan.FFmpeg；macOS: brew install ffmpeg)");
            ffmpegCheckbox.setText(ffmpegCheckbox.getText() + " [ffmpeg 未安装]");
        }
        ffmpegPanel.add(ffmpegCheckbox);
        settingsPanel.add(ffmpegPanel);

        // --- 转 MP3 后是否额外保留 FLAC ---
        JPanel keepFlacPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JCheckBox keepFlacCheckbox = new JCheckBox("转 MP3 的同时在输出目录保留原始 FLAC(否则输出目录只放 MP3)");
        keepFlacCheckbox.setSelected(currentOptions.keepFlacWithMp3);
        keepFlacCheckbox.setEnabled(ffmpegOk && currentOptions.reEncodeWithFfmpeg);
        keepFlacPanel.add(Box.createHorizontalStrut(24));
        keepFlacPanel.add(keepFlacCheckbox);
        settingsPanel.add(keepFlacPanel);

        ffmpegCheckbox.addActionListener(ev ->
                keepFlacCheckbox.setEnabled(ffmpegOk && ffmpegCheckbox.isSelected()));

        dialog.add(settingsPanel, BorderLayout.CENTER);

        // --- 按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        okButton.addActionListener(ev -> {
            currentOptions.tagMode = (TagMode) tagComboBox.getSelectedItem();
            if (ffmpegOk) {
                currentOptions.reEncodeWithFfmpeg = ffmpegCheckbox.isSelected();
                currentOptions.keepFlacWithMp3 = ffmpegCheckbox.isSelected() && keepFlacCheckbox.isSelected();
            }
            savePreferences();
            dialog.dispose();
        });
        cancelButton.addActionListener(ev -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private boolean isFfmpegInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            // 丢弃输出而不是接管道:没人读的管道填满会让子进程卡住,这里是界面线程,卡不得
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner Evaluation license - unknown
        panel = new JPanel();
        button1 = new JButton();
        button2 = new JButton();
        button3 = new JButton();
        scrollPane = new JScrollPane();
        table = new JTable();

        //======== this ========
        setIconImage(new ImageIcon(Objects.requireNonNull(View.class.getResource("/image/ico.png"))).getImage());
        setTitle("NCM2MP3");
        setMinimumSize(null);
        setVisible(true);
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 20));
        setResizable(false);
        setMaximizedBounds(null);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel ========
        {
            panel.setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.TitledBorder(new javax.swing.
                    border.EmptyBorder(0, 0, 0, 0), "author:charlottexiao", javax.swing.border.TitledBorder.CENTER
                    , javax.swing.border.TitledBorder.BOTTOM, new Font("Dia\u006cog", Font
                    .BOLD, 12), Color.red), panel.getBorder()));
            panel.addPropertyChangeListener(
                    e -> {
                        if ("bord\u0065r"
                                .equals(e.getPropertyName())) throw new RuntimeException();
                    });
            panel.setLayout(new GridLayout(1, 4, 2, 2));

            //---- button1 ----
            button1.setText("\u9009\u62e9\u6587\u4ef6");
            button1.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    button1MouseClicked(e);
                }
            });
            panel.add(button1);

            //---- button2 ----
            button2.setText("\u5f00\u59cb\u8f6c\u6362");
            button2.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    button2MouseClicked(e);
                }
            });
            panel.add(button2);

            //---- button3 ----
            button3.setText("\u6e05\u7a7a\u5217\u8868");
            button3.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    button3MouseClicked(e);
                }
            });
            panel.add(button3);

            //---- optionsButton ----
            optionsButton = new JButton("高级配置");
            optionsButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    button4MouseClicked(e);
                }
            });
            panel.add(optionsButton);
        }
        contentPane.add(panel, BorderLayout.SOUTH);

        //======== scrollPane ========
        {
            scrollPane.setPreferredSize(new Dimension(700, 300));

            //---- table ----
            table.setPreferredSize(null);
            table.setPreferredScrollableViewportSize(new Dimension(0, 0));
            table.setModel(new DefaultTableModel(
                    new Object[][]{
                    },
                    new String[]{
                            "\u97f3\u4e50\u540d", "\u6587\u4ef6\u8def\u5f84", "\u6587\u4ef6\u5927\u5c0f", "\u72b6\u6001"
                    }
            ) {
                Class<?>[] columnTypes = new Class<?>[]{
                        String.class, String.class, String.class, String.class
                };

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnTypes[columnIndex];
                }
            });
            {
                TableColumnModel cm = table.getColumnModel();
                cm.getColumn(0).setPreferredWidth(110);
                cm.getColumn(1).setPreferredWidth(400);
                cm.getColumn(2).setPreferredWidth(80);
                cm.getColumn(3).setPreferredWidth(70);
            }
            table.setRowHeight(30);
            table.setRowMargin(3);
            table.setAutoCreateRowSorter(true);
            table.setFillsViewportHeight(true);
            table.setOpaque(false);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setFocusable(false);
            table.setEnabled(false);
            scrollPane.setViewportView(table);
        }
        contentPane.add(scrollPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        // JFormDesigner - End of component initialization  //GEN-END:initComponents

        // JFormChooser1
        jFileChooser1 = new JFileChooser();
        jFileChooser1.setDialogTitle("请选择NCM音乐文件或文件夹");
        jFileChooser1.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        jFileChooser1.setMultiSelectionEnabled(true);
        jFileChooser1.setFileFilter(new FileNameExtensionFilter("网易云NCM格式音乐", "ncm"));
        //JFormChooser2
        jFileChooser2 = new JFileChooser();
        jFileChooser2.setDialogTitle("请选择保存目录");
        jFileChooser2.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jFileChooser2.setMultiSelectionEnabled(false);
        //table
        table.getTableHeader().setReorderingAllowed(false);
        //window close ways
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner Evaluation license - unknown
    private JPanel panel;
    private JButton button1;
    private JButton button2;
    private JButton button3;
    private JScrollPane scrollPane;
    private JTable table;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
    //JFormChooser1,JFormChooser2
    private JFileChooser jFileChooser1;
    private JFileChooser jFileChooser2;
    //高级配置
    private JButton optionsButton;
    private ConvertOptions currentOptions = ConvertOptions.defaults();
}
