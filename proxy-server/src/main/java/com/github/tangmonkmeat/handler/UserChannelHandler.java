package com.github.tangmonkmeat.handler;

import com.github.tangmonkmeat.config.ProxyConfig;
import com.github.tangmonkmeat.core.ProxyChannelManager;
import com.github.tangmonkmeat.web.metrics.Constants;
import com.github.tangmonkmeat.web.metrics.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description:
 * 主要处理用户的请求：
 * 1 处理用户的连接请求
 * 2 转发用户的请求
 * @author zwl
 * @version 1.0
 * @date 2021/2/27 下午3:52
 */
public class UserChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(UserChannelHandler.class);

    /**
     * 用户唯一标识 生成器
     */
    private static final AtomicInteger userIdProducer = new AtomicInteger(0);

    /**
     * 建立用户和代理服务器的channel后，通知代理客户端，建立和代理服务器的channel，
     * 为两个 channel 绑定关系
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        // 用户请求的监听端口
        InetSocketAddress localAddress = (InetSocketAddress)userChannel.localAddress();
        // 和代理客户端的channel
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(localAddress.getPort());
        if (cmdChannel == null){
            // 该端口没有代理客户端，直接断开连接
            ctx.close();
        }else {
            String userId = newUserId();
            // 内网服务信息 ip:port
            String lanInfo = ProxyConfig.instance.getLanInfo(localAddress.getPort());
            // 用户连接到代理服务器时，设置用户连接不可读，等待代理后端服务器连接成功后再改变为可读状态
            userChannel.config().setOption(ChannelOption.AUTO_READ, false);
            // 给 cmdChannel 添加和客户端连接关系
            ProxyChannelManager.addUserChannelToCmdChannel(cmdChannel,userId,userChannel);
            // 通知代理客户端，可以连接代理端口了
            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_CONNECT, userId, lanInfo.getBytes());
            cmdChannel.writeAndFlush(proxyMessage);
        }
        super.channelActive(ctx);
    }

    /**
     * 将用户请求的数据，转发给对应的代理客户端
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel userChannel = ctx.channel();
        Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
        if (proxyChannel == null){
            // 如果没有对应的代理客户端，直接关闭连接
            ctx.close();
        }else {
            byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);
            String userId = ProxyChannelManager.getUserChannelUserId(userChannel);
            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.P_TYPE_TRANSFER, userId, data);
            proxyChannel.writeAndFlush(proxyMessage);
        }
    }

    /**
     * 通知代理客户端断开指定的连接，清理缓存
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        InetSocketAddress localAddress = (InetSocketAddress)userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(localAddress.getPort());
        if (cmdChannel == null){
            // 如果没有对应的代理客户端，直接关闭连接
            ctx.close();
        }else {
            // 通知代理客户端断开指定的连接，清理缓存
            String userId = ProxyChannelManager.getUserChannelUserId(userChannel);
            ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel,userId);
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            if (proxyChannel != null && proxyChannel.isActive()){
                // 清理绑定关系
                proxyChannel.attr(Constants.NEXT_CHANNEL).set(null);
                proxyChannel.attr(Constants.CLIENT_KEY).set(null);
                proxyChannel.attr(Constants.USER_ID).set(null);

                // 设置可读
                proxyChannel.config().setOption(ChannelOption.AUTO_READ, true);
                // 通知客户端，用户连接已经断开
                ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_DISCONNECT,userId);
                proxyChannel.writeAndFlush(proxyMessage);
            }
        }
        super.channelInactive(ctx);
    }

    /**
     * 平衡用户channel和代理客户端channel的读写速度，防止OOM
     *
     * 比如：如果对方的读取速度太慢，那么我们的 OutBound缓冲区很快就会堆积大量的数据，造成OOM
     *
     * @see <a href="https://www.cnblogs.com/stateis0/p/9062155.html"/>
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(sa.getPort());
        if (cmdChannel == null) {
            // 该端口还没有代理客户端
            ctx.close();
        } else {
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            if (proxyChannel != null) {
                proxyChannel.config().setOption(ChannelOption.AUTO_READ, userChannel.isWritable());
            }
        }
        super.channelWritabilityChanged(ctx);
    }

    /**
     * 发生异常，直接关闭连接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        ctx.close();
    }

    /**
     * 生成用户连接的唯一标示
     */
    private String newUserId(){
        return String.valueOf(userIdProducer.incrementAndGet());
    }
}
