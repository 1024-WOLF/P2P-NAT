package org.github.$1024wolf.listener;


import io.netty.channel.ChannelHandlerContext;

/**
 * 通道状态监听器
 *
 * @author zwl
 */
public interface ChannelStatusListener {

    /**
     * 如果 channel 异常关闭了，重连代理服务器
     */
    void channelInactive(ChannelHandlerContext ctx);
}
