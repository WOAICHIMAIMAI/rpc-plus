package com.zheng;

import cn.hutool.core.util.IdUtil;
import com.zheng.constants.RpcConstant;
import com.zheng.model.RpcRequest;
import com.zheng.protocal.*;
import io.vertx.core.buffer.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * 优化协议测试
 */
public class OptimizedProtocolTest {

    @Test
    public void testOptimizedEncodeAndDecode() throws IOException {
        // 构造消息
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.OPTIMIZED_PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.JDK.getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        header.setBodyLength(0);
        header.setReserved((byte) 0); // 预留字段

        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setServiceName("myService");
        rpcRequest.setMethodName("myMethod");
        rpcRequest.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);
        rpcRequest.setParameterTypes(new Class[]{String.class});
        rpcRequest.setArgs(new Object[]{"aaa", "bbb"});
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcRequest);

        // 使用优化编码器
        Buffer optimizedBuffer = OptimizedProtocolMessageEncoder.encode(protocolMessage);
        
        // 使用优化解码器
        ProtocolMessage<?> decodedMessage = OptimizedProtocolMessageDecoder.decode(optimizedBuffer);
        
        Assert.assertNotNull(decodedMessage);
        Assert.assertEquals(header.getMagic(), decodedMessage.getHeader().getMagic());
        Assert.assertEquals(header.getVersion(), decodedMessage.getHeader().getVersion());
        Assert.assertEquals(header.getSerializer(), decodedMessage.getHeader().getSerializer());
        Assert.assertEquals(header.getType(), decodedMessage.getHeader().getType());
        
        System.out.println("✅ 优化协议编解码测试通过");
    }

    @Test
    public void testSerializerAndTypeCombination() {
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        
        // 测试不同的序列化器和消息类型组合
        int[] serializers = {0, 1, 2, 3}; // JDK, JSON, KRYO, HESSIAN
        int[] types = {0, 1, 2, 3}; // REQUEST, RESPONSE, HEART_BEAT, OTHERS
        
        for (int serializer : serializers) {
            for (int type : types) {
                header.setSerializerAndType(serializer, type);
                
                int extractedSerializer = header.getSerializerFromCombined();
                int extractedType = header.getTypeFromCombined();
                
                Assert.assertEquals("序列化器类型不匹配", serializer, extractedSerializer);
                Assert.assertEquals("消息类型不匹配", type, extractedType);
            }
        }
        
        System.out.println("✅ 序列化器和消息类型组合测试通过");
    }

    @Test
    public void testProtocolSizeComparison() throws IOException {
        // 构造相同的消息
        ProtocolMessage<RpcRequest> protocolMessage = createTestMessage();
        
        // 原始编码
        Buffer originalBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        
        // 优化编码
        Buffer optimizedBuffer = OptimizedProtocolMessageEncoder.encode(protocolMessage);
        
        System.out.println("=== 协议大小对比 ===");
        System.out.printf("原始协议大小: %d 字节\n", originalBuffer.length());
        System.out.printf("优化协议大小: %d 字节\n", optimizedBuffer.length());
        System.out.printf("大小差异: %d 字节\n", originalBuffer.length() - optimizedBuffer.length());
        
        // 计算节省的百分比
        double savings = ((double)(originalBuffer.length() - optimizedBuffer.length()) / originalBuffer.length()) * 100;
        System.out.printf("节省比例: %.2f%%\n", savings);
        
        // 验证优化版本确实更小或相等（因为添加了预留字段，实际大小可能相同）
        Assert.assertTrue("优化版本应该不大于原始版本", optimizedBuffer.length() <= originalBuffer.length());
        
        System.out.println("✅ 协议大小对比测试通过");
    }

    @Test
    public void testBackwardCompatibility() throws IOException {
        // 测试向后兼容性
        ProtocolMessage<RpcRequest> protocolMessage = createTestMessage();
        
        // 使用原始编码器编码
        Buffer originalBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        
        // 使用优化解码器解码（兼容模式）
        ProtocolMessage<?> decodedMessage = OptimizedProtocolMessageDecoder.decodeCompatible(originalBuffer);
        
        Assert.assertNotNull(decodedMessage);
        Assert.assertEquals(protocolMessage.getHeader().getMagic(), decodedMessage.getHeader().getMagic());
        
        System.out.println("✅ 向后兼容性测试通过");
    }

    @Test
    public void testPerformanceComparison() throws IOException {
        ProtocolMessage<RpcRequest> protocolMessage = createTestMessage();
        
        int iterations = 10000;
        
        // 测试原始编码性能
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Buffer buffer = ProtocolMessageEncoder.encode(protocolMessage);
            ProtocolMessage<?> decoded = ProtocolMessageDecoder.decode(buffer);
        }
        long originalTime = System.nanoTime() - startTime;
        
        // 测试优化编码性能
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Buffer buffer = OptimizedProtocolMessageEncoder.encode(protocolMessage);
            ProtocolMessage<?> decoded = OptimizedProtocolMessageDecoder.decode(buffer);
        }
        long optimizedTime = System.nanoTime() - startTime;
        
        System.out.println("=== 性能对比测试 ===");
        System.out.printf("测试次数: %d\n", iterations);
        System.out.printf("原始协议耗时: %.2f ms\n", originalTime / 1_000_000.0);
        System.out.printf("优化协议耗时: %.2f ms\n", optimizedTime / 1_000_000.0);
        
        double improvement = ((double)(originalTime - optimizedTime) / originalTime) * 100;
        System.out.printf("性能提升: %.2f%%\n", improvement);
        
        System.out.println("✅ 性能对比测试完成");
    }

    @Test
    public void testBitOperations() {
        // 测试位操作的正确性
        System.out.println("=== 位操作测试 ===");
        
        for (int serializer = 0; serializer < 16; serializer++) {
            for (int type = 0; type < 16; type++) {
                // 组合
                byte combined = (byte) ((serializer << 4) | (type & 0x0F));
                
                // 分离
                int extractedSerializer = (combined >> 4) & 0x0F;
                int extractedType = combined & 0x0F;
                
                Assert.assertEquals(serializer, extractedSerializer);
                Assert.assertEquals(type, extractedType);
                
                if (serializer < 4 && type < 4) {
                    System.out.printf("序列化器:%d, 类型:%d -> 组合值:0x%02X -> 序列化器:%d, 类型:%d\n", 
                            serializer, type, combined & 0xFF, extractedSerializer, extractedType);
                }
            }
        }
        
        System.out.println("✅ 位操作测试通过");
    }

    private ProtocolMessage<RpcRequest> createTestMessage() {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.OPTIMIZED_PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.JDK.getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        header.setReserved((byte) 0);

        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setServiceName("testService");
        rpcRequest.setMethodName("testMethod");
        rpcRequest.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);
        rpcRequest.setParameterTypes(new Class[]{String.class});
        rpcRequest.setArgs(new Object[]{"test"});
        
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcRequest);
        
        return protocolMessage;
    }
}
