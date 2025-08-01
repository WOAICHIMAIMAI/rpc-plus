package com.zheng.protocal;

import com.zheng.serializer.Serializer;
import com.zheng.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

/**
 * 优化的协议消息编码器
 * 将序列化器和消息类型合并到一个字节中，节省1字节空间
 */
public class OptimizedProtocolMessageEncoder {

    /**
     * 优化编码 - 使用组合字段
     *
     * @param protocolMessage
     * @return
     * @throws IOException
     */
    public static Buffer encode(ProtocolMessage<?> protocolMessage) throws IOException {
        if (protocolMessage == null || protocolMessage.getHeader() == null) {
            return Buffer.buffer();
        }
        ProtocolMessage.Header header = protocolMessage.getHeader();
        
        // 依次向缓冲区写入字节
        Buffer buffer = Buffer.buffer();
        buffer.appendByte(header.getMagic());
        buffer.appendByte(header.getVersion());
        
        // 优化：将序列化器和消息类型合并到一个字节
        header.setSerializerAndType(header.getSerializer(), header.getType());
        buffer.appendByte(header.getSerializerAndType());
        
        buffer.appendByte(header.getStatus());
        buffer.appendLong(header.getRequestId());
        
        // 获取序列化器
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化协议不存在");
        }
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());
        byte[] bodyBytes = serializer.serialize(protocolMessage.getBody());
        
        // 写入 body 长度和数据
        buffer.appendInt(bodyBytes.length);
        buffer.appendBytes(bodyBytes);
        
        // 添加预留字段
        buffer.appendByte(header.getReserved());
        
        return buffer;
    }

    /**
     * 兼容性编码 - 使用原始格式
     * 保持与旧版本的兼容性
     */
    public static Buffer encodeCompatible(ProtocolMessage<?> protocolMessage) throws IOException {
        // 直接调用原始编码器
        return ProtocolMessageEncoder.encode(protocolMessage);
    }
}
