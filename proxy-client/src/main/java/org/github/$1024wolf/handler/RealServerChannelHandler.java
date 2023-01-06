package org.github.$1024wolf.handler;

import org.github.$1024wolf.core.ClientChannelManager;
import org.github.$1024wolf.web.metrics.Constants;
import org.github.$1024wolf.web.metrics.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description:
 * 处理代理客户端和真实服务器 msg 的 handler
 * @author zwl
 * @version 1.0
 * @date 2021/2/25 下午8:53
 */
public class RealServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(RealServerChannelHandler.class);

    /**
     * 读取 真实服务器的消息，写给 代理服务器
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel realServerChannel = ctx.channel();
        // 和 代理服务器的Channel
        Channel proxyServerChannel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();
        // 如果代理客户端和代理服务器已经断开连接
        if (proxyServerChannel == null){
            // 关闭代理客户端和真实服务器的channel
            realServerChannel.close();
            return;
        }
        // 真实服务器响应的消息
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);
        String userId = ClientChannelManager.getRealServerChannelUserId(realServerChannel);
        ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.P_TYPE_TRANSFER,userId,data);
        proxyServerChannel.writeAndFlush(proxyMessage);
        logger.debug("write data to proxy server, {}, {}", realServerChannel, proxyServerChannel);
    }

    /**
     * 代理客户端和真实服务器的连接断开时，关闭移除channel，并且通知代理服务器关闭此服务
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel();
        String userId = ClientChannelManager.getRealServerChannelUserId(realServerChannel);
        // 关闭移除 realServerChannel
        ClientChannelManager.removeRealServerChannel(userId);
        Channel proxyServerChannel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();
        if (proxyServerChannel != null){
            logger.debug("channelInactive, {}", realServerChannel);
            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_DISCONNECT,userId);
            // 通知服务器端关闭指定服务
            proxyServerChannel.writeAndFlush(proxyMessage);
        }
        super.channelInactive(ctx);
    }

    /**
     * <pre>平衡读写速度，防止内存占用过多，出现OOM；</pre>
     *
     * 例如：当netty用于转发，就是从proxyServerChannel读数据，往realServerChannel写数据时，如果读写速度差距过大，容易造成OOM。
     *
     * <pre>
     * netty提供了两个标志，用来判断缓冲区的使用情况:
     * WRITE_BUFFER_HIGH_WATER_MARK;
     * WRITE_BUFFER_LOW_WATER_MARK
     * </pre>
     *
     * 1 当缓冲区水位达到高警戒线时就会触发channelWritabilityChanged回调函数，在这里注销掉对读事件的监听关闭tcp窗口；
     * 2 等水位降下来到低警戒线时再触发channelWritabilityChanged回调函数，就把读事件再给注册上去。
     *
     * @see <a href="https://www.cnblogs.com/fq0121104930716/p/12878583.html"></a>
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel();
        Channel proxyServerChannel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();
        if (proxyServerChannel != null){
            // 如果 realServerChannel 缓存区达到 WRITE_BUFFER_HIGH_WATER_MARK
            // 则，注销掉 proxyServerChannel 的读事件;
            proxyServerChannel.config().setOption(ChannelOption.AUTO_READ,realServerChannel.isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }
}
