package com.github.tangmonkmeat.web;

import com.github.tangmonkmeat.common.Container.Container;
import com.github.tangmonkmeat.config.ProxyConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Description:
 * web 控制台容器
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午11:51
 */
public class WebConfigContainer implements Container {
    private static final Logger logger = LoggerFactory.getLogger(WebConfigContainer.class);

    private final NioEventLoopGroup serverWorkerGroup;

    private final NioEventLoopGroup serverBossGroup;

    public WebConfigContainer() {

        // 配置管理，并发处理很小，使用单线程处理网络事件
        serverBossGroup = new NioEventLoopGroup(1);
        serverWorkerGroup = new NioEventLoopGroup(1);

    }

    @Override
    public void start() {
        ServerBootstrap httpServerBootstrap = new ServerBootstrap();
        httpServerBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // http 解码编码器
                        pipeline.addLast(new HttpServerCodec());
                        // 整合requestLine requestHeader 和 requestBody 为FullHttpRequest
                        pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                        // 用于支持异步写大量数据流并且不需要消耗大量内存
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpRequestHandler());
                    }
                });

        try {
            httpServerBootstrap.bind(ProxyConfig.instance.getConfigServerHost(),
                    ProxyConfig.instance.getConfigServerPort()).get();
            logger.info("http server start on port " + ProxyConfig.instance.getConfigServerPort());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        // 初始化 拦截器和接口处理器
        RouteConfig.init();
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }
}
