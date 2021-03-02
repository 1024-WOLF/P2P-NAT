package com.github.tangmonkmeat.web.metrics;

import java.util.Arrays;

/**
 * Description:
 * 代理客户端与代理服务器消息交换协议
 * @author zwl
 * @version 1.0
 * @date 2021/2/22 15:30
 */
public class ProxyMessage {

    /**
     * 认证消息，检测 clientKey 是否正确
     *
     */
    public static final byte C_TYPE_AUTH = 0x01;

    // /** 保活确认消息 */
    // public static final byte TYPE_ACK = 0x02;

    /**
     * 代理服务器建立连接的消息
     *
     */
    public static final byte TYPE_CONNECT = 0x03;

    /**
     * 代理服务器断开连接的消息
     *
     */
    public static final byte TYPE_DISCONNECT = 0x04;

    /**
     * 代理数据传输
     *
     */
    public static final byte P_TYPE_TRANSFER = 0x05;

    /**
     * 用户与代理服务器以及代理客户端与代理服务器连接是否可写状态同步
     *
     */
    public static final byte C_TYPE_WRITE_CONTROL = 0x06;

    /**
     * 心跳信息
     *
     */
    public static final byte TYPE_HEARTBEAT = 0x07;

    /** 消息类型 */
    private byte type;

    /** 消息流水号 */
    private long serialNumber;

    /** 消息命令请求信息 */
    private String uri;

    /** 消息传输数据 */
    private byte[] data;

    public ProxyMessage() {}

    public ProxyMessage(byte type, long serialNumber, String uri, byte[] data) {
        this.type = type;
        this.serialNumber = serialNumber;
        this.uri = uri;
        this.data = data;
    }

    public ProxyMessage(byte type,  String uri, byte[] data) {
        this.type = type;
        this.uri = uri;
        this.data = data;
    }

    public ProxyMessage(byte type,  String uri) {
        this.type = type;
        this.uri = uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(long serialNumber) {
        this.serialNumber = serialNumber;
    }

    @Override
    public String toString() {
        return "ProxyMessage [type=" + type + ", serialNumber=" + serialNumber + ", uri=" + uri + ", data=" + Arrays.toString(data) + "]";
    }

}
