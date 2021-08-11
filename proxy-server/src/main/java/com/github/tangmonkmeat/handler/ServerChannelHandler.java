package com.github.tangmonkmeat.handler;

import com.github.tangmonkmeat.config.ProxyConfig;
import com.github.tangmonkmeat.core.ProxyChannelManager;
import com.github.tangmonkmeat.core.ProxyServerContainer;
import com.github.tangmonkmeat.web.metrics.Constants;
import com.github.tangmonkmeat.web.metrics.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Description:
 * 处理代理客户和代理服务器的连接
 * @author zwl
 * @version 1.0
 * @date 2021/2/28 上午12:12
 */
public class ServerChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static final Logger logger = LoggerFactory.getLogger(ServerChannelHandler.class);

    /**
     * 根据代理客户端的消息类型，进行对应的处理
     *
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) throws Exception {
        logger.debug("ProxyMessage received {}", msg.getType());
        Channel channel = ctx.channel();
        switch (msg.getType()){
            case ProxyMessage.TYPE_HEARTBEAT:{
                handleHeartbeatMessage(ctx,msg);
                break;
            }
            case ProxyMessage.C_TYPE_AUTH:{
                handleAuthMessage(ctx,msg);
                break;
            }
            case ProxyMessage.TYPE_CONNECT:{
                handleConnectMessage(ctx,msg);
                break;
            }
            case ProxyMessage.P_TYPE_TRANSFER:{
                handleTransferMessage(ctx,msg);
                break;
            }
            case ProxyMessage.TYPE_DISCONNECT:{
                handleDisconnectMessage(ctx,msg);
                break;
            }
            default:break;
        }
    }

    /**
     * 处理代理客户端的断开连接请求
     */
    private void handleDisconnectMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
        // 代理连接没有连上服务器，由 cmdChannel 通知 用户断开连接
        if (clientKey == null){
            String userId = msg.getUri();
            Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(ctx.channel(), userId);
            if (userChannel != null) {
                // 数据发送完成后，关闭连接
                userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }

        // 检查clientKey
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
        // 错误的clientKey
        if (cmdChannel == null){
            logger.warn("connect message:error clientKey {}", clientKey);
            return;
        }

        // 如果代理客户端关闭或者真实服务器关闭，通知用户连接断开
        Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, ctx.channel().attr(Constants.USER_ID).get());
        if (userChannel != null) {
            // 解除绑定的关系
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(null);
            ctx.channel().attr(Constants.CLIENT_KEY).set(null);
            ctx.channel().attr(Constants.USER_ID).set(null);
            // 数据发送完成后, 关闭连接
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 转发代理客户端的请求给用户
     */
    private void handleTransferMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        // 如果userChannel已经关闭了，关闭proxyChannel
        if (userChannel == null || !userChannel.isActive()){
            logger.info("userChannel is close, userChannel=[{}]",userChannel);
            ctx.close();
            return;
        }
        byte[] data = msg.getData();
        ByteBuf buf = ctx.alloc().buffer(data.length);
        buf.writeBytes(data);
        userChannel.writeAndFlush(buf);
    }

    /**
     * 处理代理客户端连接请求，
     * 请求 uri=userId@clientKey
     */
    private void handleConnectMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        String uri = msg.getUri();
        // 如果没有携带uri，关闭连接
        if (uri == null){
            logger.warn("connect message: uri is null");
            ctx.close();
            return;
        }

        String[] tokens = uri.split("@");
        // 非法的uri，关闭连接
        if (tokens.length != 2){
            logger.warn("connect message: error uri={}", Arrays.toString(tokens));
            ctx.close();
            return;
        }

        Channel cmdChannel = ProxyChannelManager.getCmdChannel(tokens[1]);
        // clientKey 错误，关闭连接
        if (cmdChannel == null) {
            logger.warn("connect message: error clientKey={}", tokens[1]);
            ctx.close();
            return;
        }

        Channel userChannel = ProxyChannelManager.getUserChannel(cmdChannel, tokens[0]);
        if (userChannel == null){
            logger.warn("not exist userChannel, uri={}",uri);
            ctx.close();
            return;
        }
        // 绑定proxyChannel和userChannel的关系
        Channel proxyChannel = ctx.channel();
        proxyChannel.attr(Constants.USER_ID).set(tokens[0]);
        proxyChannel.attr(Constants.CLIENT_KEY).set(tokens[1]);
        proxyChannel.attr(Constants.NEXT_CHANNEL).set(userChannel);
        userChannel.attr(Constants.NEXT_CHANNEL).set(ctx.channel());
        // 代理客户端与后端服务器连接成功，修改用户连接为可读状态
        userChannel.config().setOption(ChannelOption.AUTO_READ, true);
    }

    /**
     * 处理代理客户端授权，
     * 请求必 uri=clientKey（代理客户端秘钥）
     */
    private void handleAuthMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        String clientKey = msg.getUri();
        // 获取该客户端下的映射端口
        List<Integer> ports = ProxyConfig.instance.getClientInetPorts(clientKey);
        // 授权失败，客户端秘钥错误
        if (ports == null){
            logger.info("error clientKey {}, {}", clientKey, ctx.channel());
            ctx.close();
            return;
        }

        // 第一次建立连接 cmdChannel 应该为  null
        Channel cmdChannel0 = ProxyChannelManager.getCmdChannel(clientKey);
        // 第二次试图在建立 cmdChannel
        // 授权失败，cmdChannel 已经存在
        if (cmdChannel0 != null){
            logger.warn("exist channel for key {}, {}", clientKey, cmdChannel0);
            ctx.close();
            return;
        }

        logger.info("set port => channel, {}, {}, {}", clientKey, ports, ctx.channel());
        Channel cmdChannel = ctx.channel();
        // 授权成功，设置cmdChannel相关的映射关系，缓存cmdChannel
        ProxyChannelManager.addCmdChannel(ports, clientKey, cmdChannel);

        try {
            // 开启用户端口监听
            ProxyServerContainer.startUserPort(clientKey);
        } catch (Exception e){
            logger.error("start user ports [{}] error, clientKey is [{}]",ports,clientKey);
            ctx.close();
            ProxyChannelManager.removeCmdChannel(cmdChannel);
        }
    }

    /**
     * 处理代理客户端的心跳信息
     */
    private void handleHeartbeatMessage(ChannelHandlerContext ctx,ProxyMessage msg){
        ProxyMessage heartbeatMessage = new ProxyMessage();
        heartbeatMessage.setSerialNumber(heartbeatMessage.getSerialNumber());
        heartbeatMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
        logger.debug("response heartbeat message {}", ctx.channel());
        ctx.writeAndFlush(heartbeatMessage);
        //ctx.channel().writeAndFlush(heartbeatMessage);
    }

    /**
     * 如果代理客户端断开连接：
     * 1 如果此channel有对应的userChannel；
     *      1 清理和userChannel的关系
     *      2 通知userChannel 可以关闭连接了
     *      3 移除管理器缓存的此channel的引用，便于 GC
     * 2 否则，直接清除关系
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null && userChannel.isActive()) {
            String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
            String userId = ctx.channel().attr(Constants.USER_ID).get();
            Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
            if (cmdChannel != null) {
                ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
            } else {
                logger.warn("null cmdChannel, clientKey is {}", clientKey);
            }

            // 数据发送完成后，关闭连接
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            //userChannel.close();
        } else {
            // 如果代理客户端断开连接了，解除cmdChannel绑定的关系
            // 同时，释放端口
            ProxyChannelManager.removeCmdChannel(ctx.channel());
        }
        super.channelInactive(ctx);
    }

    /**
     * <pre>平衡读写速度，防止内存占用过多，出现OOM。</pre>
     * 如果代理服务器出站缓存区数据达到  高警戒位，则触发此方法，注销 userChannel的读事件，防止OOM
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null){
            userChannel.config().setOption(ChannelOption.AUTO_READ,ctx.channel().isWritable());
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }


}
