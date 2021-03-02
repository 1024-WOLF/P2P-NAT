package com.github.tangmonkmeat.web.metrics;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;


/**
 * Description:
 *
 * @author zwl
 * @version 1.0
 * @date 2021/2/22 14:56
 */
public interface Constants {

    //

    /**
     * 下一个连接通道
     */
    AttributeKey<Channel> NEXT_CHANNEL = AttributeKey.newInstance("next_channel");

    /**
     * 连接通道的 用户唯一标识
     */
    AttributeKey<String> USER_ID = AttributeKey.newInstance("user_id");

    /**
     * 代理客户端 的唯一标识
     *
     */
    AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("client_key");
}
