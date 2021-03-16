package com.github.tangmonkmeat.web.metrics;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description:
 * 心跳检测
 * @author zwl
 * @version 1.0
 * @date 2021/2/26 下午10:57
 */
public class IdleCheckHandler extends IdleStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(IdleCheckHandler.class);

    /**
     * 读超时时间
     */
    public static final int READ_IDLE_TIME = 120;

    /**
     * 写超时时间
     */
    public static final int WRITE_IDLE_TIME = 80;

    /**
     * 实例化 心跳检测处理器，单位默认 秒，任一参数 为0都代表不开启对应的功能
     *
     * @param readerIdleTimeSeconds 读超时时间
     * @param writerIdleTimeSeconds 写超时时间
     * @param allIdleTimeSeconds 读写超时时间
     */
    public IdleCheckHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds);
    }

    /**
     * 触发读写超时，回调此方法
     *
     * 此方法用于检测对端是否还在线，可能会因为断电等原因，无法检测到对端离线
     * 或者说是替代 tcp的keepalive的机制
     *
     * 1 写超时时间小于读超时时间，会先一步发送心跳包，刷新对端的心跳时间
     * 2 如果对端没有响应心跳信息，那么，就会在读超时时间之后，关闭channel
     * 3 如果对端响应了信息，那么重置心跳时间，等待下一次超时
     */
    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        // 第一次写超时
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            logger.warn("channel first write timeout {}", ctx.channel());
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(proxyMessage);
        }
        // 第一次读超时
        else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            logger.warn("channel first read timeout {}", ctx.channel());
            ctx.channel().close();
        }
        super.channelIdle(ctx, evt);
    }
}
