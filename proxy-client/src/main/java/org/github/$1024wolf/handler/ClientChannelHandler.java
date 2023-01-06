package org.github.$1024wolf.handler;

import org.github.$1024wolf.common.Config;
import org.github.$1024wolf.core.ClientChannelManager;
import org.github.$1024wolf.listener.ChannelStatusListener;
import org.github.$1024wolf.listener.ProxyChannelBorrowListener;
import org.github.$1024wolf.web.metrics.Constants;
import org.github.$1024wolf.web.metrics.ProxyMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description:
 * 处理 代理客户端和代理服务器端的 msg 的Handler
 * @author zwl
 * @version 1.0
 * @date 2021/2/26 下午2:25
 */
public class ClientChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static final Logger logger = LoggerFactory.getLogger(ClientChannelHandler.class);

    /**
     * 代理客户端和真实服务器的 启动器
     *
     */
    private final Bootstrap realServerBootstrap;

    /**
     * 代理客户端和代理服务器的 启动器
     *
     */
    private final Bootstrap proxyServerBootstrap;

    /**
     * 通道关闭监听器
     *
     */
    private final ChannelStatusListener listener;

    public ClientChannelHandler(Bootstrap realServerBootstrap, Bootstrap proxyServerBootstrap, ChannelStatusListener listener) {
        this.realServerBootstrap = realServerBootstrap;
        this.proxyServerBootstrap = proxyServerBootstrap;
        this.listener = listener;
    }

    /**
     * 读取 代理服务器的msg，写给 真实服务器
     *
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) throws Exception {
        //ctx.writeAndFlush()

        logger.debug("received proxy message, type is {}", msg.getType());
        // 根据消息类型，分别处理
        switch (msg.getType()){
            case ProxyMessage.TYPE_CONNECT:{
                handleConnectionMessage(ctx,msg);
                break;
            }
            case ProxyMessage.P_TYPE_TRANSFER:{
                handleTransferMessage(ctx,msg);
                break;
            }
            case ProxyMessage.TYPE_DISCONNECT:{
                handleDisConnectionMessage(ctx,msg);
                break;
            }
            default: break;
        }
    }

    /**
     * 处理连接类型的消息；
     * <p>1 根据代理服务器响应的 真实服务器的ip和port，建立代理客户端和真实服务器的通道；</p>
     * <p>2 如果通道建立成功，就会再创建 代理客户端和代理服务器的通道；</p>
     * <p>3 绑定两个通道的映射关系，根据唯一标识缓存通道，为通道绑定唯一标示</p>
     * <p>4 如果和真实服务器的通道建立失败，就通知代理服务器关闭端口监听</p>
     */
    public void handleConnectionMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        Channel cmdChannel = ctx.channel();
        final String userId = msg.getUri();
        String[] realServerIpAndPort = new String(msg.getData()).split(":");
        // 真实服务器的 ip 和 port
        String ip = realServerIpAndPort[0];
        int port = Integer.parseInt(realServerIpAndPort[1]);
        // 连接真实服务器
        realServerBootstrap.connect(ip,port).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // 通道建立成功
                if (future.isSuccess()){
                    final Channel realServerChannel = future.channel();
                    logger.debug("connect realServer success, {}", realServerChannel);
                    // 由于和代理服务器的通道还未打通，所以先注销掉和真实服务器通道的读事件
                    // 避免读缓冲区内存占用过多
                    realServerChannel.config().setOption(ChannelOption.AUTO_READ,false);
                    // 建立和代理服务器的通道，同时绑定两个通道的关系
                    ClientChannelManager.borrowProxyChanel(proxyServerBootstrap, new ProxyChannelBorrowListener() {
                        @Override
                        public void success(Channel proxyServerChannel) {
                            // 两个通道绑定关系
                            proxyServerChannel.attr(Constants.NEXT_CHANNEL).set(realServerChannel);
                            realServerChannel.attr(Constants.NEXT_CHANNEL).set(proxyServerChannel);

                            // 通知代理服务器，隧道已经打通
                            String uri = userId + "@" + Config.getInstance().getStringValue("client.key");
                            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_CONNECT,uri);
                            proxyServerChannel.writeAndFlush(proxyMessage);

                            // 重新注册和真实服务器通道的读事件
                            realServerChannel.config().setOption(ChannelOption.AUTO_READ,true);
                            // 根据唯一标示缓存通道
                            ClientChannelManager.addRealServerChannel(userId,realServerChannel);
                            // 为通道绑定唯一标示
                            ClientChannelManager.setRealServerChannelUserId(realServerChannel,userId);
                        }

                        @Override
                        public void error(Throwable cause) {
                            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_DISCONNECT, userId);
                            cmdChannel.writeAndFlush(proxyMessage);
                        }
                    });
                }else{
                    ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.TYPE_DISCONNECT, userId);
                    cmdChannel.writeAndFlush(proxyMessage);
                }
            }
        });
    }

    /**
     * 处理断开连接类型的消息：
     * 1 解除隧道关系绑定
     * 2 返回proxyServerChannel给连接池
     * 3 通知真实服务器关闭 socket，然后 close channel
     */
    public void handleDisConnectionMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        Channel proxyServerChannel = ctx.channel();
        Channel realServerChannel = proxyServerChannel.attr(Constants.NEXT_CHANNEL).get();
        logger.debug("handleDisconnectMessage, {}", realServerChannel);
        if (realServerChannel != null){
            // 解除关系绑定
            proxyServerChannel.attr(Constants.NEXT_CHANNEL).set(null);
            // 返回连接池
            ClientChannelManager.returnProxyChannel(proxyServerChannel);
            // 通知真实服务器关闭 socket，然后 close channel
            realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 将消息，转发给真实服务器
     */
    public void handleTransferMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null){
            ByteBuf buf = ctx.alloc().buffer(msg.getData().length);
            buf.writeBytes(msg.getData());
            logger.debug("write data to real server, {}", realServerChannel);
            realServerChannel.writeAndFlush(buf);
        }
    }

    /**
     * 销毁控制连接的channel，回收数据连接的channel
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel proxyServerChannel = ctx.channel();
        // 如果控制连接的channel close了
        if (proxyServerChannel == ClientChannelManager.getCmdChannel()){
            // GC 控制连接的channel
            ClientChannelManager.setCmdChannel(null);
            // 通知所有的真实服务器close socket，关闭所有和真实服务器的channel
            ClientChannelManager.clearRealServerChannels();
            // 尝试重连代理服务器
            listener.channelInactive(ctx);
        }else{
            // 如果是数据传输的channel，则直接关闭
            Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
            if (realServerChannel != null && realServerChannel.isActive()){
                realServerChannel.close();
            }
        }
        // 移除连接池中的channel
        ClientChannelManager.removeProxyChannel(proxyServerChannel);
        super.channelInactive(ctx);
    }

    /**
     * <pre>平衡读写速度，防止内存占用过多，出现OOM。</pre>
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel proxyServerChannel = ctx.channel();
        Channel realServerChannel = proxyServerChannel.attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null){
            realServerChannel.config().setOption(ChannelOption.AUTO_READ,proxyServerChannel.isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }
}
