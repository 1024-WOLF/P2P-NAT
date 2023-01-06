package org.github.$1024wolf.web;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.service.impl.BlockPuzzleCaptchaServiceImpl;
import org.github.$1024wolf.common.util.JsonUtil;
import org.github.$1024wolf.config.ProxyConfig;
import org.github.$1024wolf.core.ProxyChannelManager;
import org.github.$1024wolf.web.exception.ContextException;
import org.github.$1024wolf.web.metrics.MetricsCollector;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Description:
 * 接口实现，类似于 spring 的 controller
 *
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午11:14
 */
public class RouteConfig {

    /**
     * 授权唯一标识：
     * 设置在请求头：authorization=
     * 或者设置在cookie，token=
     */
    protected static final String AUTH_COOKIE_KEY = "token";

    private static final Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    /**
     * 滑动验证码
     */
    private static final CaptchaService captchaService = new BlockPuzzleCaptchaServiceImpl();

    static {
        Properties properties = new Properties();
        try {
            properties.load(RouteConfig.class.getClassLoader().getResourceAsStream("captcha.properties"));
        } catch (IOException e) {
            logger.error("load captcha.properties error，which will use default params");
        }

        captchaService.init(properties);
    }

    /**
     * 管理员不能同时在多个地方登录
     */
    private static String token;

    public static void init() {

        // 全局拦截器，验证token
        ApiRoute.addInterceptor(new RequestHandlerInterceptor() {

            /**
             * 1 如果不是 /login 请求，必须携带token，否则触发ContextException 异常
             * 2 如果是 /login 请求，直接放行
             */
            @Override
            public void preHandler(FullHttpRequest request) {
                logger.info("handle request for api {}", request.uri());

                // 滑动验证请求，直接放行
                if ("/captcha/get".equals(request.uri()) || "/captcha/check".equals(request.uri()) || "/login".equals(request.uri())){
                    return;
                }

                // 在cookie中，验证token
                String cookieHeader = request.headers().get(HttpHeaderNames.COOKIE);
                boolean authenticated = false;
                if (cookieHeader != null) {
                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        String[] cookieArr = cookie.split("=");
                        if (AUTH_COOKIE_KEY.equals(cookieArr[0].trim())) {
                            if (cookieArr.length == 2 && cookieArr[1].equals(token)) {
                                authenticated = true;
                            }
                        }
                    }
                }

                // 在headers 中验证token
                String auth = request.headers().get(HttpHeaderNames.AUTHORIZATION);
                if (!authenticated && auth != null) {
                    String[] authArr = auth.split(" ");
                    if (authArr.length == 2 && authArr[0].equals(ProxyConfig.instance.getConfigServerUserName()) && authArr[1].equals(ProxyConfig.instance.getConfigServerPassword())) {
                        authenticated = true;
                    }
                }

                // 如果不是登录请求，且未携带 token
                if (!authenticated) {
                    throw new ContextException(ResponseInfo.CODE_UNAUTHORIZED);
                }
            }
        });

        // 获取代理客户端的配置详细信息
        ApiRoute.addRoute("/config/detail", new RequestHandler() {

            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                List<ProxyConfig.Client> clients = ProxyConfig.instance.getClients();
                for (ProxyConfig.Client client : clients) {
                    Channel channel = ProxyChannelManager.getCmdChannel(client.getClientKey());
                    // 设置代理客户端的状态
                    if (channel != null) {
                        client.setStatus(1);// online
                    } else {
                        client.setStatus(0);// offline
                    }
                }
                return ResponseInfo.build(ProxyConfig.instance.getClients());
            }
        });

        // 更新代理客户端的配置信息
        ApiRoute.addRoute("/config/update", new RequestHandler() {

            /**
             * 根据POST 的代理客户端的配置信息，更新代理客户端配置文件，更新服务器缓存的客户端配置信息
             *
             * 1 如果post 的配置信息映射有误，响应 CODE_INVILID_PARAMS
             * 2 如果更新配置信息出错，响应 CODE_INVILID_PARAMS
             */
            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf, StandardCharsets.UTF_8);
                List<ProxyConfig.Client> clients = JsonUtil.json2Object(config, new TypeToken<List<ProxyConfig.Client>>() {
                });
                // 代理客户端的配置信息错误
                if (clients == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error json config");
                }

                try {
                    // 更新代理客户端配置文件
                    ProxyConfig.instance.update(config);
                } catch (Exception ex) {
                    logger.error("config update error", ex);
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, ex.getMessage());
                }

                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });

        // 处理登录请求
        ApiRoute.addRoute("/login", new RequestHandler() {

            /**
             * 验证username和password
             */
            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String config = new String(buf);
                Map<String, String> loginParams = JsonUtil.json2Object(config, new TypeToken<Map<String, String>>() {
                });
                // 如果没有携带 用户名或者密码
                if (loginParams == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error login info");
                }
                // 验证码校验
                String verification = loginParams.get("captchaVerification");
                CaptchaVO captchaVO = new CaptchaVO();
                captchaVO.setCaptchaVerification(verification);
                ResponseModel model = captchaService.verification(captchaVO);
                if (!model.isSuccess()){
                    return ResponseInfo.build(ResponseInfo.IDENTIFYING_CODE_ERROR,"Error identifying code");
                }

                String username = loginParams.get("username");
                String password = loginParams.get("password");
                if (username == null || password == null) {
                    return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
                }
                // 验证username和password
                // 刷新token
                if (username.equals(ProxyConfig.instance.getConfigServerUserName()) && password.equals(ProxyConfig.instance.getConfigServerPassword())) {
                    token = UUID.randomUUID().toString().replace("-", "");
                    return ResponseInfo.build(token);
                }

                return ResponseInfo.build(ResponseInfo.CODE_INVILID_PARAMS, "Error username or password");
            }
        });

        // 验证码接口
        ApiRoute.addRoute("/captcha/get", new RequestHandler() {
            @Override
            public ResponseModel doService(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String json = new String(buf);
                CaptchaVO captchaVO = JsonUtil.json2Object(json, new TypeToken<CaptchaVO>() {
                });
                ResponseModel model = captchaService.get(captchaVO);
                return model;
            }
        });

        // 滑动验证码校验接口
        ApiRoute.addRoute("/captcha/check", new RequestHandler() {
            @Override
            public Object doService(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String json = new String(buf);
                CaptchaVO captchaVO = JsonUtil.json2Object(json, new TypeToken<CaptchaVO>() {
                });
                return captchaService.check(captchaVO);
            }
        });

        // 退出登录
        ApiRoute.addRoute("/logout", new RequestHandler() {
            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                token = null;
                return ResponseInfo.build(ResponseInfo.CODE_OK, "success");
            }
        });



        // 流量统计
        ApiRoute.addRoute("/metrics/get", new RequestHandler() {

            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAllMetrics());
            }
        });

        // 流量统计
        ApiRoute.addRoute("/metrics/getandreset", new RequestHandler() {

            @Override
            public ResponseInfo doService(FullHttpRequest request) {
                return ResponseInfo.build(MetricsCollector.getAndResetAllMetrics());
            }
        });
    }

}
