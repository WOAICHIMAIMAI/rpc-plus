package com.zheng.protocal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 协议消息结构
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProtocolMessage<T> {

    /**
     * 消息头
     */
    private Header header;

    /**
     * 消息体（请求或响应对象）
     */
    private T body;

    /**
     * 协议消息头
     */
    @Data
    public static class Header {

        /**
         * 魔数，保证安全性
         */
        private byte magic;

        /**
         * 版本号
         */
        private byte version;

        /**
         * 序列化器
         */
        private byte serializer;

        /**
         * 消息类型（请求 / 响应）
         */
        private byte type;

        /**
         * 序列化器和消息类型的组合字段（优化版本）
         * 高4位：序列化器类型 (0-15)
         * 低4位：消息类型 (0-15)
         */
        private byte serializerAndType;

        /**
         * 状态
         */
        private byte status;

        /**
         * 请求 id
         */
        private long requestId;

        /**
         * 消息体长度
         */
        private int bodyLength;

        /**
         * 预留字段，用于未来扩展
         */
        private byte reserved;

        /**
         * 设置序列化器和消息类型的组合值
         * @param serializer 序列化器类型 (0-15)
         * @param type 消息类型 (0-15)
         */
        public void setSerializerAndType(int serializer, int type) {
            this.serializerAndType = (byte) ((serializer << 4) | (type & 0x0F));
        }

        /**
         * 从组合字段中获取序列化器类型
         * @return 序列化器类型 (0-15)
         */
        public int getSerializerFromCombined() {
            return (serializerAndType >> 4) & 0x0F;
        }

        /**
         * 从组合字段中获取消息类型
         * @return 消息类型 (0-15)
         */
        public int getTypeFromCombined() {
            return serializerAndType & 0x0F;
        }
    }

}
