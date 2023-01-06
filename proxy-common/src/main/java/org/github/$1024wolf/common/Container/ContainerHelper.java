package org.github.$1024wolf.common.Container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Description:
 * 容器 辅助类
 * @author zwl
 * @version 1.0
 * @date 2021/2/21 23:01
 */
public class ContainerHelper {

    private static final Logger logger = LoggerFactory.getLogger(ContainerHelper.class);

    /**
     * 容器运行的状态标识：
     *  - true: 表示正在运行；
     *  - false：表示已经停止运行
     */
    private static volatile boolean running = true;

    /**
     * 缓存的容器列表
     *
     */
    private static List<Container> cachedContainers;

    /**
     * 启动所有容器
     *
     * @param containers 待启动的容器列表
     */
    public static void start(List<Container> containers){

        cachedContainers = containers;
        // 启动所有容器
        startContainers();

        // Java 语言提供一种 ShutdownHook（钩子）进制，当 JVM 接受到系统的关闭通知之后，调用 ShutdownHook 内的方法，用以完成清理操作
        // 除了主动关闭应用（使用 kill -15 指令）,以下场景也将会触发 ShutdownHook:
        // 1 代码执行结束，JVM 正常退出
        // 2 应用代码中调用 System#exit 方法
        // 3 应用中发生 OOM 错误，导致 JVM 关闭
        // 4 终端中使用 Ctrl+C(非后台运行)
        // 5 程序发生 RunTimeException 终止了程序
        // 特别注意：kill -9 并不会触发此钩子函数
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running){
                synchronized (ContainerHelper.class){
                    if (running){
                        // 停止所有容器
                        stopContainers();
                        running = false;
                        ContainerHelper.class.notify();
                    }
                }
            }
        }));

        // Main线程一直等待，直到容器全部被关闭
        synchronized (ContainerHelper.class){
            while (running){
                try {
                    ContainerHelper.class.wait();
                }catch (Exception ex){
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    /**
     * 启动所有容器
     *
     */
    private static void startContainers() {
        for (Container container : cachedContainers) {
            logger.info("starting container [{}]", container.getClass().getName());
            container.start();
            logger.info("container [{}] started", container.getClass().getName());
        }
    }

    /**
     * 关闭所有容器
     *
     */
    private static void stopContainers() {
        for (Container container : cachedContainers) {
            logger.info("stopping container [{}]", container.getClass().getName());
            try {
                container.stop();
                logger.info("container [{}] stopped", container.getClass().getName());
            } catch (Exception ex) {
                logger.warn("container stopped with error", ex);
            }
        }
    }
}
