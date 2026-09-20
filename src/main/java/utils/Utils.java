package utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * @author charlottexiao
 */
public class Utils {

    /**
     * 获取长度
     * 功能:将用小端字节排序,无符号整型4字节的长度信息转换为十进制数
     *
     * @param bytes 长度信息的字节数组
     * @return 长度
     */
    public static int getLength(byte[] bytes) {
        int len = 0;
        len |= bytes[0] & 0xff;
        len |= (bytes[1] & 0xff) << 8;
        len |= (bytes[2] & 0xff) << 16;
        len |= (bytes[3] & 0xff) << 24;
        return len;
    }

    /**
     * 读满指定长度
     * 功能:循环读取直到取满len字节,避免InputStream单次read返回不足造成的偏移错位
     *
     * @param inputStream 输入流
     * @param len         需要读取的字节数
     * @return 长度为len的字节数组
     */
    public static byte[] readBlock(InputStream inputStream, int len) throws IOException {
        byte[] bytes = new byte[len];
        int off = 0;
        while (off < len) {
            int read = inputStream.read(bytes, off, len - off);
            if (read < 0) {
                break;
            }
            off += read;
        }
        return bytes;
    }

    /**
     * 读取长度字段
     * 功能:读取4字节小端无符号整型长度
     *
     * @param inputStream 输入流
     * @return 长度
     */
    public static int readLength(InputStream inputStream) throws IOException {
        return getLength(readBlock(inputStream, 4));
    }

    /**
     * 跳过指定字节数
     * 功能:循环跳过,避免skip()实际跳过数量不足
     *
     * @param inputStream 输入流
     * @param len         需要跳过的字节数
     */
    public static void skipBlock(InputStream inputStream, long len) throws IOException {
        long remain = len;
        while (remain > 0) {
            long skipped = inputStream.skip(remain);
            if (skipped <= 0) {
                if (inputStream.read() < 0) {
                    break;
                }
                remain--;
                continue;
            }
            remain -= skipped;
        }
    }

    /**
     * 图片专辑MIME类型
     * 功能:获取图片的MIME类型
     *
     * @param albumImage 图片数据
     * @return 图片类型
     */
    public static String albumImageMimeType(byte[] albumImage) {
        byte[] mPNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};// PNG file header
        if (albumImage.length > 8) {
            for (int i = 0; i < 8; i++) {
                if (albumImage[i] != mPNG[i]) {
                    return "image/jpg";
                }
            }
        }
        return "image/png";
    }

    /**
     * 获取文件夹中所有NCM文件
     * 功能:将文件夹内的所有NCM文件存放到列表中
     *
     * @param arrayList 存放NCM文件的列表
     * @param file      文件
     */
    public static void listAllFiles(ArrayList<File> arrayList, File file) {
        if (!file.isDirectory()) {
            String name = file.getName().trim();
            if (name.length() > 3 && "ncm".equalsIgnoreCase(name.substring(name.length() - 3))) {
                arrayList.add(file);
            }
            return;
        }
        File[] files = file.listFiles();
        assert files != null;
        for (File f : files) {
            listAllFiles(arrayList, f);
        }
    }

    public static <V>  void waitForAllTask(Collection<Future<V>> futures, Predicate<V> predicateSuccess) {
        long startTime = System.nanoTime();
        int finishCnt = 0;
        int successCnt = 0;
        int failCnt = 0;
        for (Future<V> future : futures) {
            try {
                if (predicateSuccess.test(future.get())) {
                    successCnt++;
                } else {
                    failCnt++;
                }
                finishCnt++;
                System.out.format("已经完成的任务数量: %s \n", finishCnt);
            } catch (Exception e) {
                failCnt++;
                System.out.format("异步任务执行失败,异常： %s\n", e);
            }
        }
        System.out.format("所有任务执行完成,任务总数： %s, 成功数量: %s, 失败数量: %s!总共耗时: %sms", finishCnt, successCnt, failCnt,TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));

    }

}
