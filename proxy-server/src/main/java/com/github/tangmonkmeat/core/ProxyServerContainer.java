package com.github.tangmonkmeat.core;

import com.github.tangmonkmeat.common.Container.Container;
import com.github.tangmonkmeat.config.ProxyConfig;
import com.github.tangmonkmeat.handler.ServerChannelHandler;
import com.github.tangmonkmeat.handler.UserChannelHandler;
import com.github.tangmonkmeat.web.handler.BytesMetricsHandler;
import com.github.tangmonkmeat.web.metrics.Constants;
import com.github.tangmonkmeat.web.metrics.IdleCheckHandler;
import com.github.tangmonkmeat.web.metrics.ProxyMessageDecoder;
import com.github.tangmonkmeat.web.metrics.ProxyMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.util.List;

/**
 * Description:
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/2 上午12:03
 */
public class ProxyServerContainer implements Container, ProxyConfig.ConfigChangedListener {

    private static final Logger logger = LoggerFactory.getLogger(ProxyServerContainer.class);

    /**
     * 最大的 传输数据 3M
     */
    private static final int MAX_FRAME_LENGTH = 3 * 1024 * 1024;

    private static final int LENGTH_FIELD_OFFSET = 0;

    private static final int LENGTH_FIELD_LENGTH = 4;

    private static final int INITIAL_BYTES_TO_STRIP = 0;

    private static final int LENGTH_ADJUSTMENT = 0;

    private final NioEventLoopGroup serverWorkerGroup;

    private final NioEventLoopGroup serverBossGroup;

    private static ServerBootstrap usersBootstrap;

    private static ServerBootstrap clientsBootstrap;

    public ProxyServerContainer() {
        serverBossGroup = new NioEventLoopGroup();
        serverWorkerGroup = new NioEventLoopGroup();
        ProxyConfig.instance.addConfigChangedListener(this);
    }

    @Override
    public void start() {
        // 保证 clientsBootstrap 单例
        if (clientsBootstrap == null){
            synchronized (ProxyServerContainer.class){
                if (clientsBootstrap == null){
                    clientsBootstrap = new ServerBootstrap();
                    clientsBootstrap.group(serverBossGroup, serverWorkerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(new ChannelInitializer<SocketChannel>() {

                                @Override
                                public void initChannel(SocketChannel ch) throws Exception {
                                    ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                                    ch.pipeline().addLast(new ProxyMessageEncoder());
                                    ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME, 0));
                                    ch.pipeline().addLast(new ServerChannelHandler());
                                }
                            });

                    try {
                        clientsBootstrap.bind(ProxyConfig.instance.getServerHost(), ProxyConfig.instance.getServerPort()).get();
                        logger.info("proxy server start on port " + ProxyConfig.instance.getServerPort());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        // 保证 usersBootstrap 单例
        if (usersBootstrap == null){
            synchronized (ProxyServerContainer.class){
                if (usersBootstrap == null){
                    usersBootstrap = new ServerBootstrap();
                    usersBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            // 统计用户流量
                            ch.pipeline().addFirst(new BytesMetricsHandler());
                            // 转发用户请求
                            ch.pipeline().addLast(new UserChannelHandler());
                        }
                    });
                }
            }
        }
    }

    /**
     * 开启所有映射的端口，用于处理用户的请求
     *
     * @param clientKey 代理客户端的key
     */
    public static void startUserPort(String clientKey) {
        // 根据clientKey开启 代理指定代理客户端映射的端口
        List<Integer> ports = ProxyConfig.instance.getClientInetPorts(clientKey);
        for (int port : ports) {
            try {
                ChannelFuture future = usersBootstrap.bind(port);
                future.get();
                Channel bindChannel = future.channel();
                // 缓存 port:bindChannel
                ProxyChannelManager.addBindChannel(port,bindChannel);
                logger.info("bind user port {}, clientKey {}", port, clientKey);
            } catch (Exception ex) {
                // 该端口已经绑定过，直接忽略
                // 如果不是 BindException，抛出异常
                if (!(ex.getCause() instanceof BindException)) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    /**
     * 开启所有用户的所有的port
     *
     */
    private void startAllUserPort(){
        List<ProxyConfig.Client> clients = ProxyConfig.instance.getClients();
        for(ProxyConfig.Client c : clients){
            // 如果用户在线，端口更新
            if (c.getStatus() == 1){
                startUserPort(c.getClientKey());
            }
        }
    }

    /**
     * 代理客户端的配置信息更新时，回调此方法
     */
    @Override
    public void onChanged() {
        //startAllUserPort();
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }
}

