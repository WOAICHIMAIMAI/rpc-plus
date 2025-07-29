package com.zheng.server.tcp;

import cn.hutool.core.util.IdUtil;

import com.zheng.RpcApplication;
import com.zheng.model.RpcRequest;
import com.zheng.model.RpcResponse;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.protocal.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Vertx TCP 请求客户端 - 优化版本
 */
@Slf4j
public class VertxTcpClient {

    // 单例Vertx实例
    private static final Vertx vertx = Vertx.vertx();

    // 连接池 - 按服务地址缓存NetClient
    private static final ConcurrentHashMap<String, NetClient> clientPool = new ConcurrentHashMap<>();

    // 连接池配置
    private static final int MAX_POOL_SIZE = 10;
    private static final int CONNECT_TIMEOUT = 5000; // 5秒连接超时
    private static final int IDLE_TIMEOUT = 30000; // 30秒空闲超时

    /**
     * 获取或创建NetClient
     */
    private static NetClient getOrCreateClient(String serviceKey) {
        return clientPool.computeIfAbsent(serviceKey, key -> {
            NetClientOptions options = new NetClientOptions()
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setIdleTimeout(IDLE_TIMEOUT)
                    .setTcpKeepAlive(true)
                    .setTcpNoDelay(true)
                    .setReconnectAttempts(3)
                    .setReconnectInterval(1000);

            return vertx.createNetClient(options);
        });
    }

    /**
     * 发送请求 - 优化版本
     *
     * @param rpcRequest
     * @param serviceMetaInfo
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) throws InterruptedException, ExecutionException {
        long startTime = System.currentTimeMillis();

        String serviceKey = serviceMetaInfo.getServiceHost() + ":" + serviceMetaInfo.getServicePort();
        NetClient netClient = getOrCreateClient(serviceKey);

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

        // 预先构造消息，减少连接回调中的处理时间
        ProtocolMessage<RpcRequest> protocolMessage = buildProtocolMessage(rpcRequest);
        Buffer encodeBuffer;
        try {
            encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
        } catch (IOException e) {
            log.error("协议消息编码错误", e);
            throw new RuntimeException("协议消息编码错误", e);
        }

        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(),
                result -> {
                    if (!result.succeeded()) {
                        log.error("Failed to connect to TCP server: {}", result.cause().getMessage());
                        responseFuture.completeExceptionally(new RuntimeException("连接失败", result.cause()));
                        return;
                    }

                    NetSocket socket = result.result();

                    // 设置响应处理器
                    TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(
                            buffer -> {
                                try {
                                    ProtocolMessage<RpcResponse> rpcResponseProtocolMessage =
                                            (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                                    responseFuture.complete(rpcResponseProtocolMessage.getBody());

                                    // 关闭socket，但保留client用于复用
                                    socket.close();
                                } catch (IOException e) {
                                    log.error("协议消息解码错误", e);
                                    responseFuture.completeExceptionally(new RuntimeException("协议消息解码错误", e));
                                    socket.close();
                                }
                            }
                    );
                    socket.handler(bufferHandlerWrapper);

                    // 发送数据
                    socket.write(encodeBuffer);
                });

        try {
            RpcResponse rpcResponse = responseFuture.get(10, TimeUnit.SECONDS); // 10秒超时
            long endTime = System.currentTimeMillis();
            log.debug("TCP请求耗时: {}ms", endTime - startTime);
            return rpcResponse;
        } catch (Exception e) {
            log.error("TCP请求超时或失败", e);
            throw new RuntimeException("TCP请求超时或失败", e);
        }
    }

    /**
     * 构造协议消息
     */
    private static ProtocolMessage<RpcRequest> buildProtocolMessage(RpcRequest rpcRequest) {
        ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(RpcApplication.getRpcConfig().getSerializer()).getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setRequestId(IdUtil.getSnowflakeNextId());
        protocolMessage.setHeader(header);
        protocolMessage.setBody(rpcRequest);
        return protocolMessage;
    }

    /**
     * 清理连接池
     */
    public static void cleanup() {
        clientPool.values().forEach(NetClient::close);
        clientPool.clear();
    }

    /**
     * 关闭Vertx实例
     */
    public static void shutdown() {
        cleanup();
        vertx.close();
    }
}
