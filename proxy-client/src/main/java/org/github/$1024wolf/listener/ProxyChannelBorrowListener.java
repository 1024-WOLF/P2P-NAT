package org.github.$1024wolf.listener;

import io.netty.channel.Channel;

/**
 * 通道建立监听器
 *
 * @author zwl
 */
public interface ProxyChannelBorrowListener {

    /**
     * <p>代理客户端和真实服务器，以及代理服务器的通道打通后，回调此方法<p/>
     *
     * 1 绑定 真实服务器<---->代理客户端 和 代理客户端<--->代理服务器的两个通道的关系；
     * 2 同时，将通道缓存到连接池
     *
     * @param proxyServerChannel 代理客户端<--->代理服务器的通道
     */
    void success(Channel proxyServerChannel);

    /**
     * 如果 代理客户端<--->代理服务器的 通道建立失败，就通知代理服务器断开和代理客户端的连接
     *
     * @param cause 异常信息
     */
    void error(Throwable cause);

}
