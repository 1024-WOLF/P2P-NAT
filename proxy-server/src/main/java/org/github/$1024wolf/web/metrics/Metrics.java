package org.github.$1024wolf.web.metrics;

import java.io.Serializable;

/**
 * Description:
 * 代理服务器指定端口的流量统计数据实体，统计所有流经该端口的数据
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午8:17
 */
public class Metrics implements Serializable {

    /**
     * 统计流量的端口
     */
    private int port;

    /**
     * 代理服务器 读取用户请求的总字节数
     */
    private long readBytes;

    /**
     * 代理服务器 写给用户的总字节数
     */
    private long wroteBytes;

    /**
     * 读取数据的次数
     */
    private long readMsgs;

    /**
     * 写出数据的次数
     */
    private long wroteMsgs;

    /**
     * 代理服务器指定的 {@link Metrics#port}  所持有的 channel 个数
     */
    private int channels;

    /**
     * 时间戳
     */
    private long timestamp;

    public long getReadBytes() {
        return readBytes;
    }

    public void setReadBytes(long readBytes) {
        this.readBytes = readBytes;
    }

    public long getWroteBytes() {
        return wroteBytes;
    }

    public void setWroteBytes(long wroteBytes) {
        this.wroteBytes = wroteBytes;
    }

    public long getReadMsgs() {
        return readMsgs;
    }

    public void setReadMsgs(long readMsgs) {
        this.readMsgs = readMsgs;
    }

    public long getWroteMsgs() {
        return wroteMsgs;
    }

    public void setWroteMsgs(long wroteMsgs) {
        this.wroteMsgs = wroteMsgs;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
