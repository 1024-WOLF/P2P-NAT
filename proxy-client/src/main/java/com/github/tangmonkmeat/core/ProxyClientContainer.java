package com.github.tangmonkmeat.core;

import com.github.tangmonkmeat.common.Config;
import com.github.tangmonkmeat.common.Container.Container;
import com.github.tangmonkmeat.handler.ClientChannelHandler;
import com.github.tangmonkmeat.handler.RealServerChannelHandler;
import com.github.tangmonkmeat.listener.ChannelStatusListener;
import com.github.tangmonkmeat.web.metrics.IdleCheckHandler;
import com.github.tangmonkmeat.web.metrics.ProxyMessage;
import com.github.tangmonkmeat.web.metrics.ProxyMessageDecoder;
import com.github.tangmonkmeat.web.metrics.ProxyMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description:
 * 代理客户端容器
 * @author zwl
 * @version 1.0
 * @date 2021/2/26 下午10:05
 */
public class ProxyClientContainer implements Container, ChannelStatusListener {

    private static final Logger logger = LoggerFactory.getLogger(ProxyClientContainer.class);

    /**
     * 转发的最大数据帧大小，1M
     */
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    /**
     * 记录数据帧总长度的数据在数据帧中的偏移量，0
     */
    private static final int LENGTH_FIELD_OFFSET = 0;

    /**
     * 记录数据帧总长度的字节数，4 byte
     */
    private static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * 丢弃处于有效数据前面的字节数量，0
     */
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    /**
     * 长度域的偏移量矫正，0
     */
    private static final int LENGTH_ADJUSTMENT = 0;

    /**
     * 线程池
     */
    private final NioEventLoopGroup workerGroup;

    /**
     * 和真实服务器相关的启动器
     */
    private final Bootstrap realServerBootstrap = new Bootstrap();

    /**
     * 和代理服务器相关的启动器
     */
    private final Bootstrap proxyServerBootstrap = new Bootstrap();

    /**
     * 代理客户端配置
     */
    private final Config config = Config.getInstance();

    /**
     * 重连代理服务器 间隔；
     *
     * 每次重连失败后，延长等待时间 2倍；如果等待时间 > 60s,就重置为 1s
     */
    private long sleepTimeMill = 1000;

    /**
     * 重连代理服务器的次数
     */
    private final AtomicInteger retry = new AtomicInteger(0);

    public ProxyClientContainer(){
        // 默认线程数是 cpu核心数的2倍
        workerGroup = new NioEventLoopGroup();
        realServerBootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new RealServerChannelHandler());
                    }
                });
        proxyServerBootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH,LENGTH_FIELD_OFFSET,LENGTH_FIELD_LENGTH,LENGTH_ADJUSTMENT,INITIAL_BYTES_TO_STRIP));
                        pipeline.addLast(new ProxyMessageEncoder());
                        pipeline.addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME,IdleCheckHandler.WRITE_IDLE_TIME - 10,0));
                        pipeline.addLast(new ClientChannelHandler(realServerBootstrap,proxyServerBootstrap, ProxyClientContainer.this));
                    }
                });
    }

    /**
     * 连接代理服务器
     */
    private void connectProxyServer(){
        proxyServerBootstrap.connect(config.getStringValue("server.host"),config.getIntValue("server.port"))
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        // 连接成功,向服务器发送客户端认证信息（clientKey）
                        if (future.isSuccess()){
                            Channel cmdChannel = future.channel();
                            // 缓存控制连接的channel
                            ClientChannelManager.setCmdChannel(cmdChannel);
                            // 发送认证信息给代理服务器
                            ProxyMessage proxyMessage = new ProxyMessage(ProxyMessage.C_TYPE_AUTH, config.getStringValue("client.key"));
                            cmdChannel.writeAndFlush(proxyMessage);
                            sleepTimeMill = 1000;
                            logger.info("connect proxy server success, {}", future.channel());
                        }else {
                            // 递归重连代理服务器
                            if (retry.getAndAdd(1) < config.getIntValue("client.retryMaxCount")) {
                                logger.warn("connect proxy server failed {} retry，wait {}ms",retry.get(), sleepTimeMill, future.cause());
                                // 连接失败，等待重连
                                reconnectWait();
                                connectProxyServer();
                            }else {
                                System.exit(-1);
                            }
                        }
                    }
                });
    }

    /**
     * 等待重连代理服务器
     */
    private void reconnectWait(){
        try {
            if (sleepTimeMill > 60000) {
                sleepTimeMill = 1000;
            }

            synchronized (this) {
                sleepTimeMill = sleepTimeMill * 2;
                wait(sleepTimeMill);
            }
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * 尝试连接代理服务器
     */
    @Override
    public void start() {
        connectProxyServer();
    }

    /**
     * 等待任务全部结束，关闭线程池
     */
    @Override
    public void stop() {
        // 等待所有任务结束，关闭线程池
        workerGroup.shutdownGracefully();
    }

    /**
     * 如果控制连接的通道意外关闭，尝试重连代理服务器
     *
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 递归重连代理服务器
        if (retry.getAndAdd(1) < config.getIntValue("client.retryMaxCount")) {
            logger.warn("connect proxy server failed {} retry，wait {}ms",retry.get(), sleepTimeMill);
            // 连接失败，等待重连
            reconnectWait();
            connectProxyServer();
        }else {
            System.exit(-1);
        }
    }
}
