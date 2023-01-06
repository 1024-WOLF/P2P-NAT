package org.github.$1024wolf.web.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Description:
 * 代理消息编码器
 * @author zwl
 * @version 1.0
 * @date 2021/2/22 20:57
 */
public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> implements LengthFieldConstants{


    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        int bodyLength = TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE;
        byte[] uriBytes = null;
        String uri = msg.getUri();
        if (uri != null){
            uriBytes = uri.getBytes();
            bodyLength += uriBytes.length;
        }
        byte[] data = msg.getData();
        if (data != null){
            bodyLength += data.length;
        }

        out.writeInt(bodyLength);
        out.writeByte(msg.getType());
        out.writeLong(msg.getSerialNumber());
        if (uriBytes != null){
            out.writeByte(uriBytes.length);
            out.writeBytes(uriBytes);
        }else {
            out.writeByte(0x00);
        }
        if (data != null){
            out.writeBytes(data);
        }
    }
}
