package com.zheng.protocal;

/**
 * 协议常量
 */
public interface ProtocolConstant {

    /**
     * 消息头长度（原始版本）
     */
    int MESSAGE_HEADER_LENGTH = 17;

    /**
     * 消息头长度（优化版本）
     * 将序列化器和消息类型合并，节省1字节，但添加1字节预留字段
     */
    int OPTIMIZED_MESSAGE_HEADER_LENGTH = 17;

    /**
     * 协议魔数
     */
    byte PROTOCOL_MAGIC = 0x1;

    /**
     * 协议版本号（原始版本）
     */
    byte PROTOCOL_VERSION = 0x1;

    /**
     * 协议版本号（优化版本）
     */
    byte OPTIMIZED_PROTOCOL_VERSION = 0x2;
}
