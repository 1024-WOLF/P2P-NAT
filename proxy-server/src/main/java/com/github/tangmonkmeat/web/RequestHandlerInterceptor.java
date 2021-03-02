package com.github.tangmonkmeat.web;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * Description:
 * 接口请求拦截
 * @author zwl
 * @date 2021/3/1 下午10:08
 * @version 1.0
 */
public interface RequestHandlerInterceptor {

    /**
     * 请求预处理
     *
     * @param request {@link FullHttpRequest}
     */
    void preHandler(FullHttpRequest request);
}
