package com.zheng.server.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP连接池管理器
 */
@Slf4j
public class TcpConnectionPool {
    
    private static final Vertx vertx = Vertx.vertx();
    private static final ConcurrentHashMap<String, ConnectionPool> pools = new ConcurrentHashMap<>();
    
    // 连接池配置
    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_POOL_SIZE = 5;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int IDLE_TIMEOUT = 60000;
    
    /**
     * 获取连接
     */
    public static CompletableFuture<NetSocket> getConnection(String host, int port) {
        String key = host + ":" + port;
        ConnectionPool pool = pools.computeIfAbsent(key, k -> new ConnectionPool(host, port));
        return pool.getConnection();
    }
    
    /**
     * 归还连接
     */
    public static void returnConnection(String host, int port, NetSocket socket) {
        String key = host + ":" + port;
        ConnectionPool pool = pools.get(key);
        if (pool != null) {
            pool.returnConnection(socket);
        } else {
            socket.close();
        }
    }
    
    /**
     * 关闭所有连接池
     */
    public static void shutdown() {
        pools.values().forEach(ConnectionPool::close);
        pools.clear();
        vertx.close();
    }
    
    /**
     * 单个服务的连接池
     */
    private static class ConnectionPool {
        private final String host;
        private final int port;
        private final NetClient netClient;
        private final ConcurrentLinkedQueue<NetSocket> availableConnections = new ConcurrentLinkedQueue<>();
        private final AtomicInteger totalConnections = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        
        public ConnectionPool(String host, int port) {
            this.host = host;
            this.port = port;
            
            NetClientOptions options = new NetClientOptions()
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setIdleTimeout(IDLE_TIMEOUT)
                    .setTcpKeepAlive(true)
                    .setTcpNoDelay(true)
                    .setReconnectAttempts(3)
                    .setReconnectInterval(1000);
            
            this.netClient = vertx.createNetClient(options);
            
            // 预创建最小连接数
            for (int i = 0; i < MIN_POOL_SIZE; i++) {
                createConnection();
            }
        }
        
        public CompletableFuture<NetSocket> getConnection() {
            // 尝试从池中获取可用连接
            NetSocket socket = availableConnections.poll();
            if (socket != null && !socket.writeQueueFull()) {
                activeConnections.incrementAndGet();
                return CompletableFuture.completedFuture(socket);
            }
            
            // 如果没有可用连接且未达到最大连接数，创建新连接
            if (totalConnections.get() < MAX_POOL_SIZE) {
                return createConnection();
            }
            
            // 等待连接归还（简化实现，实际应该有超时机制）
            return waitForConnection();
        }
        
        private CompletableFuture<NetSocket> createConnection() {
            CompletableFuture<NetSocket> future = new CompletableFuture<>();
            
            netClient.connect(port, host, result -> {
                if (result.succeeded()) {
                    NetSocket socket = result.result();
                    totalConnections.incrementAndGet();
                    activeConnections.incrementAndGet();
                    
                    // 设置连接关闭处理器
                    socket.closeHandler(v -> {
                        totalConnections.decrementAndGet();
                        activeConnections.decrementAndGet();
                        log.debug("连接已关闭: {}:{}", host, port);
                    });
                    
                    future.complete(socket);
                    log.debug("创建新连接: {}:{}, 总连接数: {}", host, port, totalConnections.get());
                } else {
                    log.error("连接创建失败: {}:{}, 错误: {}", host, port, result.cause().getMessage());
                    future.completeExceptionally(result.cause());
                }
            });
            
            return future;
        }
        
        private CompletableFuture<NetSocket> waitForConnection() {
            // 简化实现：直接创建新连接（实际应该实现等待队列）
            return createConnection();
        }
        
        public void returnConnection(NetSocket socket) {
            if (socket != null && !socket.writeQueueFull()) {
                activeConnections.decrementAndGet();
                availableConnections.offer(socket);
                log.debug("连接已归还: {}:{}, 可用连接数: {}", host, port, availableConnections.size());
            }
        }
        
        public void close() {
            // 关闭所有连接
            NetSocket socket;
            while ((socket = availableConnections.poll()) != null) {
                socket.close();
            }
            netClient.close();
            log.info("连接池已关闭: {}:{}", host, port);
        }
        
        public int getTotalConnections() {
            return totalConnections.get();
        }
        
        public int getActiveConnections() {
            return activeConnections.get();
        }
        
        public int getAvailableConnections() {
            return availableConnections.size();
        }
    }
    
    /**
     * 获取连接池统计信息
     */
    public static void printPoolStats() {
        System.out.println("=== TCP连接池统计 ===");
        pools.forEach((key, pool) -> {
            System.out.printf("服务: %s, 总连接: %d, 活跃连接: %d, 可用连接: %d%n",
                    key, pool.getTotalConnections(), pool.getActiveConnections(), pool.getAvailableConnections());
        });
    }
}
