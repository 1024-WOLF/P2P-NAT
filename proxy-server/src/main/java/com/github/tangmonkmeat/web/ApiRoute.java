package com.github.tangmonkmeat.web;

import com.github.tangmonkmeat.web.exception.ContextException;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Description:
 * 接口路由管理
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午10:59
 */
public class ApiRoute {

    private static final Logger logger = LoggerFactory.getLogger(ApiRoute.class);

    /**
     * 接口路由
     * key：url
     * value：RequestHandler
     */
    private static final Map<String, RequestHandler> routes = new ConcurrentHashMap<>();

    /**
     * 拦截器
     */
    private static final Set<RequestHandlerInterceptor> middlewares = new HashSet<>();

    /**
     * 增加接口请求路由；如果uri已经存在，触发 {@link IllegalArgumentException} 异常
     *
     * @param uri            请求接口
     * @param requestHandler 接口处理器
     */
    public static void addRoute(String uri, RequestHandler requestHandler) {
        // 接口路由已经存在
        if (routes.containsKey(uri)) {
            throw new IllegalArgumentException("uri is existed:" + uri);
        }

        logger.info("add route {}", uri);
        routes.put(uri, requestHandler);
    }

    /**
     * 增加拦截器；如果拦截器已经存在，触发 {@link IllegalArgumentException} 异常
     *
     * @param interceptor 请求拦截器
     */
    public static void addInterceptor(RequestHandlerInterceptor interceptor) {
        // 拦截器已经存在
        if (middlewares.contains(interceptor)) {
            throw new IllegalArgumentException("interceptor is existed:" + interceptor);
        }

        logger.info("add requestMiddleware {}", interceptor);
        middlewares.add(interceptor);
    }

    /**
     * 请求执行；如果触发异常返回异常响应；如果不是 {@link ContextException}返回 null
     *
     * @param request {@link FullHttpRequest}
     * @return 请求响应 {@link ResponseInfo}
     */
    public static ResponseInfo execute(FullHttpRequest request) {
        try {
            // 请求预处理
            for (RequestHandlerInterceptor interceptor : middlewares) {
                interceptor.preHandler(request);
            }

            URI uri = new URI(request.uri());
            RequestHandler handler = routes.get(uri.getPath());
            ResponseInfo responseInfo;
            if (handler != null) {
                // 处理请求
                responseInfo = handler.doService(request);
            } else {
                // 请求的资源不存在
                responseInfo = ResponseInfo.build(ResponseInfo.CODE_API_NOT_FOUND, "api not found");
            }
            return responseInfo;
        } catch (Exception ex) {
            if (ex instanceof ContextException) {
                return ResponseInfo.build(((ContextException) ex).getCode(), ex.getMessage());
            }
            logger.error("request error", ex);
        }
        return null;
    }

}
