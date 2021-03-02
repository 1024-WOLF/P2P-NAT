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
    public static final int READ_IDLE_TIME = 60;

    /**
     * 写超时时间
     */
    public static final int WRITE_IDLE_TIME = 40;

    /**
     * 实例化 心跳检测处理器，单位默认 秒
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
