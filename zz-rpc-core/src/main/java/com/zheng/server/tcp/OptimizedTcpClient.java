package com.zheng.server.tcp;

import cn.hutool.core.util.IdUtil;
import com.zheng.RpcApplication;
import com.zheng.model.RpcRequest;
import com.zheng.model.RpcResponse;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.protocal.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化的TCP客户端 - 使用连接池
 */
@Slf4j
public class OptimizedTcpClient {
    
    // 请求ID映射，用于处理异步响应
    private static final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    
    // 性能统计
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalTime = new AtomicLong(0);
    
    /**
     * 发送请求 - 使用连接池优化
     */
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) 
            throws InterruptedException, ExecutionException {
        long startTime = System.nanoTime();
        totalRequests.incrementAndGet();
        
        try {
            // 从连接池获取连接
            CompletableFuture<NetSocket> connectionFuture = TcpConnectionPool.getConnection(
                    serviceMetaInfo.getServiceHost(), serviceMetaInfo.getServicePort());
            
            NetSocket socket = connectionFuture.get(5, TimeUnit.SECONDS);
            
            // 构造协议消息
            ProtocolMessage<RpcRequest> protocolMessage = buildProtocolMessage(rpcRequest);
            long requestId = protocolMessage.getHeader().getRequestId();
            
            // 创建响应Future
            CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
            pendingRequests.put(requestId, responseFuture);
            
            // 设置响应处理器（只设置一次）
            if (!socket.writeQueueFull()) {
                setupResponseHandler(socket, serviceMetaInfo);
            }
            
            // 编码并发送请求
            Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
            socket.write(encodeBuffer);
            
            // 等待响应
            RpcResponse response = responseFuture.get(10, TimeUnit.SECONDS);
            
            // 归还连接到池中
            TcpConnectionPool.returnConnection(serviceMetaInfo.getServiceHost(), 
                    serviceMetaInfo.getServicePort(), socket);
            
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000; // 转换为毫秒
            totalTime.addAndGet(duration);
            
            log.debug("请求完成，耗时: {}ms, 请求ID: {}", duration, requestId);
            return response;
            
        } catch (Exception e) {
            log.error("TCP请求失败", e);
            throw new RuntimeException("TCP请求失败", e);
        }
    }
    
    /**
     * 设置响应处理器
     */
    private static void setupResponseHandler(NetSocket socket, ServiceMetaInfo serviceMetaInfo) {
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            try {
                ProtocolMessage<RpcResponse> responseMessage = 
                        (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                
                long requestId = responseMessage.getHeader().getRequestId();
                CompletableFuture<RpcResponse> future = pendingRequests.remove(requestId);
                
                if (future != null) {
                    future.complete(responseMessage.getBody());
                } else {
                    log.warn("收到未知请求ID的响应: {}", requestId);
                }
                
            } catch (IOException e) {
                log.error("协议消息解码错误", e);
                // 完成所有等待的请求（异常情况）
                pendingRequests.values().forEach(future -> 
                        future.completeExceptionally(new RuntimeException("协议消息解码错误", e)));
                pendingRequests.clear();
            }
        });
        
        socket.handler(bufferHandlerWrapper);
        
        // 设置异常处理器
        socket.exceptionHandler(throwable -> {
            log.error("Socket异常: {}", throwable.getMessage());
            // 完成所有等待的请求（异常情况）
            pendingRequests.values().forEach(future -> 
                    future.completeExceptionally(new RuntimeException("Socket异常", throwable)));
            pendingRequests.clear();
        });
    }
    
    /**
     * 构造协议消息
     */
    private static ProtocolMessage<RpcRequest> buildProtocolMessage(RpcRequest rpcRequest) {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(
                RpcApplication.getRpcConfig().getSerializer()).getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcRequest);
        return protocolMessage;
    }
    
    /**
     * 获取性能统计信息
     */
    public static void printPerformanceStats() {
        long requests = totalRequests.get();
        long time = totalTime.get();
        
        if (requests > 0) {
            double avgTime = (double) time / requests;
            System.out.println("=== TCP客户端性能统计 ===");
            System.out.println("总请求数: " + requests);
            System.out.println("总耗时: " + time + "ms");
            System.out.println("平均耗时: " + String.format("%.2f", avgTime) + "ms");
            System.out.println("QPS: " + String.format("%.2f", requests * 1000.0 / time));
        }
    }
    
    /**
     * 重置统计信息
     */
    public static void resetStats() {
        totalRequests.set(0);
        totalTime.set(0);
    }
    
    /**
     * 清理资源
     */
    public static void cleanup() {
        pendingRequests.clear();
        TcpConnectionPool.shutdown();
    }
}
