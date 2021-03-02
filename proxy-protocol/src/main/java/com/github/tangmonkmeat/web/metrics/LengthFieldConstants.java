package com.github.tangmonkmeat.web.metrics;

/**
 * Description:
 *
 * @author zwl
 * @version 1.0
 * @date 2021/2/22 21:01
 */
public interface LengthFieldConstants {

    // 指定代理消息的消息头的固定长度，避免粘包拆包的发生

    /**
     * 代理消息的总长度（此项必须），在消息的首部；
     * 固定以 4 byte 记录；<br />
     *
     * 注意：如果代理消息的总长度 不足 4 byte，认定为非法消息。
     *
     */
    byte HEADER_SIZE = 4;

    /**
     * 代理消息的类型（此项必须），详细可见 {@link ProxyMessage}；
     * 位于 代理消息的 第二处；
     * 固定以 1 byte 记录。
     *
     */
    byte TYPE_SIZE = 1;

    /**
     * 代理消息的流水号（此项必须）；
     * 位于 代理消息的 第三处；
     * 固定以 8 byte 记录。
     *
     */
    byte SERIAL_NUMBER_SIZE = 8;

    /**
     * 代理消息的 URL 的长度（此项必须）；
     * 位于 代理消息的 第四处；
     * 固定以 1 byte 记录。
     *
     */
    byte URI_LENGTH_SIZE = 1;

    // url：代理消息的 uri
    // data：代理消息的有效数据，位于代理消息的末尾，长度不固定。

}
