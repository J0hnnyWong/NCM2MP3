package executor;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author charlottexiao
 */
public class AsyncTaskExecutor {

    /**
     * 转换是 IO + ffmpeg 编码的混合活,线程越多不代表越快:
     * 每个线程可能各起一个 ffmpeg 进程,开太多会把整台机器拖到没响应
     */
    private static final int THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    /**
     * 固定大小 + 无界队列:提交任务只会入队,绝不会像有界队列那样
     * 触发 CallerRunsPolicy 把转换放回调用者线程 —— 那正是界面点下"开始转换"后卡死的原因
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    /**
     * 执行异步任务
     *
     * @param task 异步任务
     * @return 任务结果句柄
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }
}
