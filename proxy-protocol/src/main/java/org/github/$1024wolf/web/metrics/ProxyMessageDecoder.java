package org.github.$1024wolf.web.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Description:
 * 代理消息解码器 extends LengthFieldBasedFrameDecoder
 * <p>
 *     例如，自定义长度解码器的构造参数值如下：
 *     <pre>LengthFieldBasedFrameDecoder spliter = new LengthFieldBasedFrameDecoder(1024,0,4,0,4);</pre>
 *     第一个参数为1024，表示数据包的最大长度为1024；
 *     第二个参数0，表示长度域的偏移量为0，也就是长度域放在了最前面，处于包的起始位置；
 *     第三个参数为4，表示长度域占用4个字节；
 *     第四个参数为0，表示长度域保存的值，仅仅为有效数据长度，不包含其他域（如长度域）的长度；
 *     第五个参数为4，表示最终的取到的目标数据包，抛弃最前面的4个字节数据，长度域的值被抛弃。
 * <p/>
 * @author zwl
 * @version 1.0
 * @date 2021/2/22 17:07
 */
public class ProxyMessageDecoder extends LengthFieldBasedFrameDecoder implements LengthFieldConstants {

    /**
     * 初始化 自定义长度帧解码器
     *
     * @param maxFrameLength
     *        发送的数据包最大长度；
     * @param lengthFieldOffset
     *        长度域偏移量，指的是长度域位于整个数据包字节数组中的下标；
     * @param lengthFieldLength
     *        长度域的自己的字节数长度；
     * @param lengthAdjustment
     *        长度域的偏移量矫正；
     *        如果长度域的值，除了包含有效数据域的长度外，还包含了其他域（如长度域自身）长度，
     *        那么，就需要进行矫正。矫正的值为：包长 - 长度域的值 – 长度域偏移 – 长度域长；
     * @param initialBytesToStrip
     *        丢弃的起始字节数。丢弃处于有效数据前面的字节数量。比如前面有4个节点的长度域，则它的值为4。
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    /**
     * 初始化 自定义长度帧解码器
     *
     * @param maxFrameLength
     *        发送的数据包最大长度；
     * @param lengthFieldOffset
     *        长度域偏移量，指的是长度域位于整个数据包字节数组中的下标；
     * @param lengthFieldLength
     *        长度域的自己的字节数长度；
     * @param lengthAdjustment
     *        长度域的偏移量矫正；
     *        如果长度域的值，除了包含有效数据域的长度外，还包含了其他域（如长度域自身）长度，
     *        那么，就需要进行矫正。矫正的值为：包长 - 长度域的值 – 长度域偏移 – 长度域长；
     * @param initialBytesToStrip
     *        丢弃的起始字节数。丢弃处于有效数据前面的字节数量。比如前面有4个节点的长度域，则它的值为4。
     * @param failFast
     *        如果为true，则表示读取到长度域，它的值的超过maxFrameLength，就抛出一个 TooLongFrameException，
     *        而为false表示只有当真正读取完长度域的值表示的字节之后，才会抛出 TooLongFrameException，
     *        默认情况下设置为true，建议不要修改，否则可能会造成内存溢出。
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    }

    @Override
    protected ProxyMessage decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        Channel channel = ctx.channel();

        // 由 LengthFieldBasedFrameDecoder 解码，返回的自定义长度的数据帧
        ByteBuf buf = (ByteBuf)super.decode(ctx, in);

        ProxyMessage proxyMessage;
        try {
            if (buf == null){
                return null;
            }

            int readableBytes = buf.readableBytes();

            // 数据帧非法，记录数据帧长度的值不完整
            if (readableBytes < HEADER_SIZE){
                return null;
            }

            // 数据帧长度
            int frameLength = buf.readInt();
            // 数据帧部分遗失
            if (readableBytes < frameLength){
                return null;
            }

            byte type = buf.readByte();
            long serialNumber = buf.readLong();
            byte uriLen = buf.readByte();
            byte[] uriBytes = new byte[uriLen];
            if (uriLen != 0){
                buf.readBytes(uriBytes);
            }
            byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLen];
            buf.readBytes(data);
            proxyMessage = new ProxyMessage(type,serialNumber,new String(uriBytes),data);
            return proxyMessage;
        } finally {
             // 防止内存泄露
             // 将 ByteBuf 池 中的 ByteBuf 的引用计数减 1
             // 如果 计数为 0，则回收 ByteBuf
            if (buf != null){
                buf.release();
            }
        }
    }
}
