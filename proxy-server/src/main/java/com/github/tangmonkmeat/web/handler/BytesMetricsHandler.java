package com.github.tangmonkmeat.web.handler;

import com.github.tangmonkmeat.web.metrics.MetricsCollector;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.InetSocketAddress;

/**
 * Description:
 * 流量统计处理器，
 * ChannelDuplexHandler实现了ChannelInboundHandlerAdapter和ChannelOutboundHandler两个接口，同时可以处理入站和出站事件
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午8:41
 */
public class BytesMetricsHandler extends ChannelDuplexHandler {

    /**
     * 统计入站的流量
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        MetricsCollector metricsCollector = MetricsCollector.getCollector(sa.getPort());
        // 字节叠加
        metricsCollector.incrementReadBytes(((ByteBuf) msg).readableBytes());
        // 读取次数叠加
        metricsCollector.incrementReadMsgs(1);
        ctx.fireChannelRead(msg);
    }

    /**
     * 统计出站的流量
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        MetricsCollector metricsCollector = MetricsCollector.getCollector(sa.getPort());
        // 字节叠加
        metricsCollector.incrementWroteBytes(((ByteBuf) msg).readableBytes());
        // 写出次数叠加
        metricsCollector.incrementWroteMsgs(1);
        super.write(ctx, msg, promise);
    }

    /**
     * 有新用户连接，创建并且缓存 此连接的流量计数器
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        MetricsCollector.getCollector(sa.getPort()).getChannels().incrementAndGet();
        super.channelActive(ctx);
    }

    /**
     * 用户连接断开，清理 此连接的流量计数器
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().localAddress();
        MetricsCollector.getCollector(sa.getPort()).getChannels().decrementAndGet();
        super.channelInactive(ctx);
    }

}
