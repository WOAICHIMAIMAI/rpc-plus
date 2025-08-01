package com.zheng.protocal;

import com.zheng.model.RpcRequest;
import com.zheng.model.RpcResponse;
import com.zheng.serializer.Serializer;
import com.zheng.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

/**
 * 优化的协议消息解码器
 * 支持解析组合字段格式的协议消息
 */
public class OptimizedProtocolMessageDecoder {

    /**
     * 优化解码 - 解析组合字段
     *
     * @param buffer
     * @return
     * @throws IOException
     */
    public static ProtocolMessage<?> decode(Buffer buffer) throws IOException {
        // 检查消息长度，判断是否为优化格式
        if (buffer.length() < ProtocolConstant.MESSAGE_HEADER_LENGTH + 1) {
            // 长度不足，使用兼容解码
            return ProtocolMessageDecoder.decode(buffer);
        }
        
        // 分别从指定位置读出 Buffer
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        byte magic = buffer.getByte(0);
        
        // 校验魔数
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            throw new RuntimeException("消息 magic 非法");
        }
        
        header.setMagic(magic);
        header.setVersion(buffer.getByte(1));
        
        // 优化：从组合字段中解析序列化器和消息类型
        byte serializerAndType = buffer.getByte(2);
        header.setSerializerAndType(serializerAndType);
        
        // 设置分离的字段值（保持兼容性）
        header.setSerializer((byte) header.getSerializerFromCombined());
        header.setType((byte) header.getTypeFromCombined());
        
        header.setStatus(buffer.getByte(3));
        header.setRequestId(buffer.getLong(4));
        header.setBodyLength(buffer.getInt(12));
        
        // 读取预留字段
        if (buffer.length() >= 17) {
            header.setReserved(buffer.getByte(16));
        }
        
        // 解决粘包问题，只读指定长度的数据
        int bodyStartIndex = 16; // 优化后的消息体起始位置
        byte[] bodyBytes = buffer.getBytes(bodyStartIndex, bodyStartIndex + header.getBodyLength());
        
        // 解析消息体
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化消息的协议不存在");
        }
        
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());
        ProtocolMessageTypeEnum messageTypeEnum = ProtocolMessageTypeEnum.getEnumByKey(header.getType());
        if (messageTypeEnum == null) {
            throw new RuntimeException("序列化消息的类型不存在");
        }
        
        switch (messageTypeEnum) {
            case REQUEST:
                RpcRequest request = serializer.deserialize(bodyBytes, RpcRequest.class);
                return new ProtocolMessage<>(header, request);
            case RESPONSE:
                RpcResponse response = serializer.deserialize(bodyBytes, RpcResponse.class);
                return new ProtocolMessage<>(header, response);
            case HEART_BEAT:
            case OTHERS:
            default:
                throw new RuntimeException("暂不支持该消息类型");
        }
    }

    /**
     * 兼容性解码 - 使用原始格式
     * 自动检测并处理旧格式的消息
     */
    public static ProtocolMessage<?> decodeCompatible(Buffer buffer) throws IOException {
        try {
            // 先尝试优化解码
            return decode(buffer);
        } catch (Exception e) {
            // 如果失败，回退到原始解码
            return ProtocolMessageDecoder.decode(buffer);
        }
    }

    /**
     * 检测消息格式版本
     * @param buffer
     * @return true表示优化格式，false表示原始格式
     */
    public static boolean isOptimizedFormat(Buffer buffer) {
        if (buffer.length() < 3) {
            return false;
        }
        
        // 检查魔数和版本
        byte magic = buffer.getByte(0);
        byte version = buffer.getByte(1);
        
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            return false;
        }
        
        // 可以通过版本号或其他标识来判断格式
        // 这里简单地通过消息长度来判断
        return buffer.length() >= ProtocolConstant.MESSAGE_HEADER_LENGTH + 1;
    }
}
