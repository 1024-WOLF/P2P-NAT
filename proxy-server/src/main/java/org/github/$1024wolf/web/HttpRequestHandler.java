package org.github.$1024wolf.web;

import com.anji.captcha.model.common.ResponseModel;
import org.github.$1024wolf.common.util.JsonUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Description:
 * Http 请求处理器
 * @author zwl
 * @version 1.0
 * @date 2021/3/1 下午10:11
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    /**
     * //private static final String DEFAULT_PREFIX =  System.getProperty("user.dir") + "/pages";
     * 静态资源路径
     */
    private static final String DEFAULT_PREFIX;


    static {
        String base_dir = System.getProperty("base.dir",System.getProperty("user.dir"));
        DEFAULT_PREFIX = base_dir + "/pages";
        //if(base_dir != null){
        //    DEFAULT_PREFIX = base_dir + "/pages";
        //}else{
        //    try {
        //        DEFAULT_PREFIX = HttpRequestHandler.class.getClassLoader().getResource("").getPath() + "pages";
        //    } catch (Exception e) {
        //        e.printStackTrace();
        //    }
        //}
    }

    /**
     * 代理服务器的版本等信息；
     * 响应头：server=PAT/0.1
     */
    private static final String SERVER_VS = "PAT/0.1";

    /**
     * 根据请求方式（GET/POST），分别进行处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

        // GET请求
        if (request.method() != HttpMethod.POST) {
            outputPages(ctx, request);
            return;
        }

        // POST 请求
        Object responseInfo = ApiRoute.execute(request);
        assert responseInfo != null;

        if (responseInfo instanceof ResponseInfo){
            ResponseInfo responseInfo1 = (ResponseInfo) responseInfo;
            // 错误码规则：除100取整为http状态码
            outputContent(ctx, request, responseInfo1.getCode() / 100, JsonUtil.object2Json(responseInfo),
                    "Application/json;charset=utf-8");
        }else if (responseInfo instanceof ResponseModel){
            ResponseModel responseModel = (ResponseModel) responseInfo;
            if ("0000".equals(responseModel.getRepCode())){
                responseModel.setRepCode("200");
            }
            // 错误码规则：除100取整为http状态码
            outputContent(ctx, request, Integer.parseInt(responseModel.getRepCode()), JsonUtil.object2Json(responseInfo),
                    "Application/json;charset=utf-8");
        }
    }

    /**
     * 响应 POST请求，返回JSON等的数据
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param request {@link FullHttpRequest}
     * @param code 响应状态码
     * @param body 响应体
     * @param mimeType 响应的MIME类型
     */
    private void outputContent(ChannelHandlerContext ctx, FullHttpRequest request,
                               int code, String body, String mimeType){

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code),
                // 零拷贝
                Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
        // 响应头
        response.headers().set(HttpHeaderNames .CONTENT_TYPE, mimeType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.SERVER, SERVER_VS);
        // 响应
        ChannelFuture future = ctx.writeAndFlush(response);
        // 如果不是长连接，直接关闭channel
        if (!HttpUtil.isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 响应 GET请求，返回页面
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param request {@link FullHttpRequest}
     */
    private void outputPages(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException, IOException {
        HttpResponseStatus status = HttpResponseStatus.OK;
        URI uri = new URI(request.uri());
        String uriPath = uri.getPath();
        // 默认访问 index.html
        uriPath = "/".equals(uriPath) ? "/index.html" : uriPath;
        String path = DEFAULT_PREFIX + uriPath;
        File rfile = new File(path);
        if (rfile.isDirectory()) {
            path = path + "/index.html";
            rfile = new File(path);
        }

        // 静态资源不存在
        if (!rfile.exists()) {
            status = HttpResponseStatus.NOT_FOUND;
            outputContent(ctx, request, status.code(), status.toString(), "text/html");
            return;
        }

        // 征询服务器能否继续请求
        if (HttpUtil.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        // 访问静态济源
        long length = 0;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(rfile, "r");
            length = raf.length();
        } finally {
            if (length < 0) {
                raf.close();
            }
        }

        /*
         设置响应数据
         */
        HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), status);
        // 根据请求资源的后缀，获取MIME类型
        String mimeType = MimeType.getMimeType(MimeType.parseSuffix(path));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType);
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,"inline");

        // 如果请求是长连接
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
        }

        response.headers().set(HttpHeaderNames.SERVER, SERVER_VS);
        // 响应
        ctx.write(response);
        // 零拷贝
        ctx.write(new DefaultFileRegion(raf.getChannel(), 0, length));

        // 如果不是长连接，直接关闭channel
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * http 100-continue用于客户端在发送POST数据给服务器前，征询服务器情况，看服务器是否处理POST的数据;
     * 如果不处理，客户端则不上传POST数据;
     * 如果处理，则POST上传数据;
     * 一般在POST大数据时，才会使用100-continue协议。
     *
     * 如果客户端有POST数据要上传，可以考虑使用100-continue协议。加入头{"Expect":"100-continue"}；
     *
     * 服务端策略：
     * 1 正确情况下，收到请求后，返回100或错误码；
     * 2 如果在发送100-continue前收到了POST数据（客户端提前发送POST数据），则不发送100响应码(略去)。
     *
     */
    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        ctx.writeAndFlush(response);
    }
}
