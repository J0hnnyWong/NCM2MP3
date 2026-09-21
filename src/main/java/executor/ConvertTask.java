package executor;

import service.ConvertOptions;
import service.Converter;

import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import java.util.concurrent.Callable;

/**
 * @author charlottexiao
 */
public class ConvertTask implements Callable<Boolean> {

    private static final int STATUS_COLUMN = 3;

    private final String ncmFilePath;
    private final String outFilePath;
    private final ConvertOptions options;
    private final TableModel model;
    private final int rowIndex;
    private final Runnable onFinished;

    /**
     * ControllerThread初始化
     *
     * @param ncmFilePath ncm文件路径
     * @param outFilePath 输出路径
     */
    public ConvertTask(String ncmFilePath, String outFilePath, TableModel model, int rowIndex) {
        this(ncmFilePath, outFilePath, ConvertOptions.defaults(), model, rowIndex, null);
    }

    /**
     * ControllerThread初始化
     *
     * @param ncmFilePath ncm文件路径
     * @param outFilePath 输出路径
     * @param options     转换配置选项
     * @param onFinished  单首结束后的回调,在事件分发线程上执行(用于进度显示)
     */
    public ConvertTask(String ncmFilePath, String outFilePath, ConvertOptions options, TableModel model,
                       int rowIndex, Runnable onFinished) {
        this.ncmFilePath = ncmFilePath;
        this.outFilePath = outFilePath;
        this.options = options;
        this.model = model;
        this.rowIndex = rowIndex;
        this.onFinished = onFinished;
        status("转换中..");
    }

    /**
     * 线程执行方法:NCM文件转换,并修改其转换状态
     */
    public Boolean call() {
        boolean ok = new Converter().ncm2Mp3(ncmFilePath, outFilePath, options);
        status(ok ? "转换完毕" : "转换失败");
        if (onFinished != null) {
            SwingUtilities.invokeLater(onFinished);
        }
        return ok;
    }

    /**
     * 表格只能在事件分发线程上改,工作线程直接 setValueAt 会和重绘/排序器打架,界面就挂住了
     */
    private void status(String text) {
        SwingUtilities.invokeLater(() -> model.setValueAt(text, rowIndex, STATUS_COLUMN));
    }

}
