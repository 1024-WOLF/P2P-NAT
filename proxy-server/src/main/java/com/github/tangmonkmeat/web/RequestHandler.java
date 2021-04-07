package com.github.tangmonkmeat.web;

import io.netty.handler.codec.http.FullHttpRequest;

/**
 * Description:
 * 接口请求处理
 * @author zwl
 * @date 2021/3/1 下午10:06
 * @version 1.0
 */
public interface RequestHandler {

    /**
     * 请求处理
     *
     * @param request {@link FullHttpRequest}
     * @return {@link ResponseInfo}
     */
    Object doService(FullHttpRequest request);
}
