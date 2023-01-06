package org.github.$1024wolf.web.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Description:
 * 代理服务器的流量统计计数器
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午8:23
 */
public class MetricsCollector {

    /**
     * 缓存代理服务器每个端口的流量数据
     * key: port;
     * value: MetricsCollector
     */
    private static final Map<Integer, MetricsCollector> METRICS_COLLECTORS = new ConcurrentHashMap<>();

    private Integer port;

    /**
     * 读入字节计数器
     */
    private final AtomicLong readBytes = new AtomicLong();

    /**
     * 写出字节计数器
     */
    private final AtomicLong wroteBytes = new AtomicLong();

    /**
     * 读入次数计数器
     */
    private final AtomicLong readMsgs = new AtomicLong();

    /**
     * 写出次数计数器
     */
    private final AtomicLong wroteMsgs = new AtomicLong();

    /**
     * port 关联 channel 计数器
     */
    private final AtomicInteger channels = new AtomicInteger();

    private MetricsCollector() {}

    /**
     * 根据端口，获取流量统计数据
     * 
     * 如果指定端口的 MetricsCollector不存在，则返回一个空的 MetricsCollector
     * 
     * @param port 代理服务器的端口
     * @return 流量统计数据 MetricsCollector
     */
    public static MetricsCollector getCollector(Integer port) {
        MetricsCollector collector = METRICS_COLLECTORS.get(port);
        // 双重校验锁，保证单例
        if (collector == null) {
            synchronized (METRICS_COLLECTORS) {
                collector = METRICS_COLLECTORS.get(port);
                if (collector == null) {
                    collector = new MetricsCollector();
                    collector.setPort(port);
                    METRICS_COLLECTORS.put(port, collector);
                }
            }
        }
        return collector;
    }

    /**
     * 返回所有端口的流量统计数据（使用自旋锁，保证线程安全）
     * 
     * @return Metrics 列表
     */
    public static List<Metrics> getAndResetAllMetrics() {
        List<Metrics> allMetrics = new ArrayList<>();
        for (Map.Entry<Integer, MetricsCollector> integerMetricsCollectorEntry : METRICS_COLLECTORS.entrySet()) {
            allMetrics.add(integerMetricsCollectorEntry.getValue().getAndResetMetrics());
        }

        return allMetrics;
    }

    /**
     * 返回所有端口的流量统计数据
     *
     * @return Metrics 列表
     */
    public static List<Metrics> getAllMetrics() {
        List<Metrics> allMetrics = new ArrayList<>();
        for (Map.Entry<Integer, MetricsCollector> integerMetricsCollectorEntry : METRICS_COLLECTORS.entrySet()) {
            allMetrics.add(integerMetricsCollectorEntry.getValue().getMetrics());
        }

        return allMetrics;
    }

    public Metrics getAndResetMetrics() {
        Metrics metrics = new Metrics();
        metrics.setChannels(channels.get());
        metrics.setPort(port);
        metrics.setReadBytes(readBytes.getAndSet(0));
        metrics.setWroteBytes(wroteBytes.getAndSet(0));
        metrics.setTimestamp(System.currentTimeMillis());
        metrics.setReadMsgs(readMsgs.getAndSet(0));
        metrics.setWroteMsgs(wroteMsgs.getAndSet(0));

        return metrics;
    }

    public Metrics getMetrics() {
        Metrics metrics = new Metrics();
        metrics.setChannels(channels.get());
        metrics.setPort(port);
        metrics.setReadBytes(readBytes.get());
        metrics.setWroteBytes(wroteBytes.get());
        metrics.setTimestamp(System.currentTimeMillis());
        metrics.setReadMsgs(readMsgs.get());
        metrics.setWroteMsgs(wroteMsgs.get());

        return metrics;
    }

    public void incrementReadBytes(long bytes) {
        readBytes.addAndGet(bytes);
    }

    public void incrementWroteBytes(long bytes) {
        wroteBytes.addAndGet(bytes);
    }

    public void incrementReadMsgs(long msgs) {
        readMsgs.addAndGet(msgs);
    }

    public void incrementWroteMsgs(long msgs) {
        wroteMsgs.addAndGet(msgs);
    }

    public AtomicInteger getChannels() {
        return channels;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
