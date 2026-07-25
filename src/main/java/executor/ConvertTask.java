package executor;

import service.ConvertOptions;
import service.Converter;
import service.tag.TagMode;

import javax.swing.table.TableModel;
import java.util.concurrent.Callable;

/**
 * @author charlottexiao
 */
public class ConvertTask implements Callable<Boolean> {

    private final String ncmFilePath;
    private final String outFilePath;
    private final ConvertOptions options;
    private final TableModel model;
    private final int rowIndex;

    /**
     * ControllerThread初始化
     *
     * @param ncmFilePath ncm文件路径
     * @param outFilePath 输出路径
     */
    public ConvertTask(String ncmFilePath, String outFilePath, TableModel model, int rowIndex) {
        this(ncmFilePath, outFilePath, ConvertOptions.defaults(), model, rowIndex);
    }

    /**
     * ControllerThread初始化
     *
     * @param ncmFilePath ncm文件路径
     * @param outFilePath 输出路径
     * @param options     转换配置选项
     */
    public ConvertTask(String ncmFilePath, String outFilePath, ConvertOptions options, TableModel model, int rowIndex) {
        this.ncmFilePath = ncmFilePath;
        this.outFilePath = outFilePath;
        this.options = options;
        this.model = model;
        this.rowIndex = rowIndex;
        model.setValueAt("转换中..", rowIndex, 3);
    }

    /**
     * 线程执行方法:NCM文件转换,并修改器转换状态
     */
    public Boolean call() {
        if (new Converter().ncm2Mp3(ncmFilePath, outFilePath, options)) {
            model.setValueAt("转换完毕", rowIndex, 3);
            return true;
        } else {
            model.setValueAt("转换失败", rowIndex, 3);
            return false;
        }
    }

}
