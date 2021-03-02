package com.github.tangmonkmeat.core;

import com.github.tangmonkmeat.common.Config;
import com.github.tangmonkmeat.listener.ProxyChannelBorrowListener;
import com.github.tangmonkmeat.web.metrics.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Description:
 * 连接管理器，
 * 连接：
 * 代理客户端<--->代理服务器
 * 真实服务器<--->代理客户端
 * @author zwl
 * @version 1.0
 * @date 2021/2/25 下午9:10
 */
public class ClientChannelManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientChannelManager.class);

    /**
     * 和代理服务器的连接池的Channel的最大数量
     */
    private static final int MAX_POOL_SIZE = 100;

    /**
     * 和代理服务器的Channel的连接池
     */
    private static final ConcurrentLinkedQueue<Channel> PROXY_CHANNEL_POOL = new ConcurrentLinkedQueue<Channel>();

    /**
     * 和真实服务器的 Channel 集合
     * key：userId
     * value：Channel
     */
    private static final Map<String, Channel> REAL_SERVER_CHANNELS = new ConcurrentHashMap<>();

    /**
     * 获取 代理服务器端注册的隧道映射关系的channel
     */
    private static volatile Channel cmdChannel;

    /**
     * 代理配置
     */
    private static final Config CONFIG = Config.getInstance();

    public static Channel getCmdChannel() {
        return cmdChannel;
    }

    public static void setCmdChannel(Channel cmdChannel) {
        ClientChannelManager.cmdChannel = cmdChannel;
    }

    /**
     * 代理客户端和真实服务器建立通道后调用此方法；
     * 开启 代理客户端和代理服务端 映射隧道；
     * 然后，绑定 真实服务器和代理服务器的 的映射关系
     *
     * @param proxyServerBootstrap 建立代理客户端和代理服务器连接的启动器
     * @param listener 建立隧道的监听器
     */
    public static void borrowProxyChanel(Bootstrap proxyServerBootstrap, final ProxyChannelBorrowListener listener){
        // 返回并移除队列队头的channel
        Channel channel = PROXY_CHANNEL_POOL.poll();
        // 如果有可用的channel
        if (channel != null){
            // 绑定 隧道映射关系
            // 真实服务器<--->代理客户端；代理客户端<--->代理服务器
            listener.success(channel);
            return;
        }

        // 建立隧道
        proxyServerBootstrap.connect(CONFIG.getStringValue("server.host"),CONFIG.getIntValue("server.port"))
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        // 连接成功
                        if (future.isSuccess()){
                            // 绑定关系
                            listener.success(future.channel());
                        }else {
                            logger.warn("connect proxy server failed", future.cause());
                            // 通知代理服务器关闭此代理服务（关闭端口监听）
                            listener.error(future.cause());
                        }
                    }
                });
    }

    /**
     * 解除proxyChannel和真实服务器的关系，归还 channel 给代理连接池
     *
     * @param proxyChannel proxyChannel
     */
    public static void returnProxyChannel(Channel proxyChannel){
        // 如果超过最大的连接数量，直接close
        if (PROXY_CHANNEL_POOL.size() > MAX_POOL_SIZE){
            proxyChannel.close();
        }else {
            proxyChannel.config().setOption(ChannelOption.AUTO_READ,true);
            // 解除proxyChannel和真实服务器的关系
            proxyChannel.attr(Constants.NEXT_CHANNEL).set(null);
            // 添加到连接池队尾
            PROXY_CHANNEL_POOL.offer(proxyChannel);
            logger.debug("return ProxyChanel to the pool, channel is {}, pool current size is {} ", proxyChannel, PROXY_CHANNEL_POOL.size());
        }
    }

    /**
     * 移除连接池中指定的 proxyChannel
     *
     * @param proxyChannel proxyChannel
     */
    public static void removeProxyChannel(Channel proxyChannel){
        PROXY_CHANNEL_POOL.remove(proxyChannel);
    }

    /**
     * 设置 代理客户端和真实服务器的channel的唯一标示
     *
     * @param realServerChannel 代理客户端和真实服务器的channel
     * @param userId 唯一标识
     */
    public static void setRealServerChannelUserId(Channel realServerChannel,String userId){
        realServerChannel.attr(Constants.USER_ID).set(userId);
    }

    /**
     * 获取 代理客户端和真实服务器的channel的唯一标示
     *
     * @param realServerChannel 代理客户端和真实服务器的channel
     * @return channel的唯一标示
     */
    public static String getRealServerChannelUserId(Channel realServerChannel){
        return realServerChannel.attr(Constants.USER_ID).get();
    }

    /**
     * 根据指定 的 channel 唯一标示 userId 获取 channel
     *
     * @param userId realServerChannel 唯一标识
     * @return realServerChannel
     */
    public static Channel getRealServerChannel(String userId) {
        return REAL_SERVER_CHANNELS.get(userId);
    }

    /**
     * 添加 channel
     *
     * @param userId channel 唯一标示
     * @param realServerChannel realServerChannel
     */
    public static void addRealServerChannel(String userId, Channel realServerChannel) {
        REAL_SERVER_CHANNELS.put(userId, realServerChannel);
    }

    /**
     * 移除指定的 realServerChannel，并返回
     *
     * @param userId realServerChannel 唯一标识
     * @return realServerChannel
     */
    public static Channel removeRealServerChannel(String userId) {
        return REAL_SERVER_CHANNELS.remove(userId);
    }

    /**
     * 移除所有的 realServerChannel，如果 realServerChannel 没有close，通知真实服务端关闭socket
     *
     */
    public static void clearRealServerChannels() {
        logger.warn("channel closed, clear real server channels");

        for (Map.Entry<String, Channel> stringChannelEntry : REAL_SERVER_CHANNELS.entrySet()) {
            Channel realServerChannel = stringChannelEntry.getValue();
            if (realServerChannel.isActive()) {
                // 通知真实服务端关闭socket
                realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        // 清空连接池
        REAL_SERVER_CHANNELS.clear();
    }

}
