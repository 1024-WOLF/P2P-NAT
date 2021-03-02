package com.github.tangmonkmeat.core;

import com.github.tangmonkmeat.common.Container.Container;
import com.github.tangmonkmeat.config.ProxyConfig;
import com.github.tangmonkmeat.handler.ServerChannelHandler;
import com.github.tangmonkmeat.handler.UserChannelHandler;
import com.github.tangmonkmeat.web.handler.BytesMetricsHandler;
import com.github.tangmonkmeat.web.metrics.IdleCheckHandler;
import com.github.tangmonkmeat.web.metrics.ProxyMessageDecoder;
import com.github.tangmonkmeat.web.metrics.ProxyMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
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
     * 最大的 传输数据 2M
     */
    private static final int MAX_FRAME_LENGTH = 2 * 1024 * 1024;

    private static final int LENGTH_FIELD_OFFSET = 0;

    private static final int LENGTH_FIELD_LENGTH = 4;

    private static final int INITIAL_BYTES_TO_STRIP = 0;

    private static final int LENGTH_ADJUSTMENT = 0;

    private final NioEventLoopGroup serverWorkerGroup;

    private final NioEventLoopGroup serverBossGroup;

    public ProxyServerContainer() {
        serverBossGroup = new NioEventLoopGroup();
        serverWorkerGroup = new NioEventLoopGroup();
        ProxyConfig.instance.addConfigChangedListener(this);
    }

    @Override
    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup)
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
            bootstrap.bind(ProxyConfig.instance.getServerHost(), ProxyConfig.instance.getServerPort()).get();
            logger.info("proxy server start on port " + ProxyConfig.instance.getServerPort());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }


        startUserPort();
    }

    /**
     * 开启所有映射的端口
     *
     */
    private void startUserPort() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addFirst(new BytesMetricsHandler());
                ch.pipeline().addLast(new UserChannelHandler());
            }
        });

        List<Integer> ports = ProxyConfig.instance.getUserPorts();
        for (int port : ports) {
            try {
                bootstrap.bind(port).get();
                logger.info("bind user port " + port);
            } catch (Exception ex) {
                // 该端口已经绑定过
                if (!(ex.getCause() instanceof BindException)) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    /**
     * 代理客户端的配置信息更新时，回调此方法
     */
    @Override
    public void onChanged() {
        startUserPort();
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }
}

