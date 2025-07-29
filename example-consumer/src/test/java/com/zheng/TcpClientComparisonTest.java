package com.zheng;

import com.zheng.model.RpcRequest;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.model.User;
import com.zheng.registry.LocalRegistry;
import com.zheng.server.tcp.OptimizedTcpClient;
import com.zheng.server.tcp.TcpConnectionPool;
import com.zheng.server.tcp.VertxTcpClient;
import com.zheng.server.tcp.VertxTcpServer;
import com.zheng.service.UserService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP客户端性能对比测试
 */
public class TcpClientComparisonTest {

    private static final String TCP_HOST = "localhost";
    private static final int TCP_PORT = 8888;

    // 简单的UserService实现类
    public static class TestUserServiceImpl implements UserService {
        @Override
        public User getUser(User user) {
            return user;
        }
    }

    @BeforeClass
    public static void setupTcpServer() throws Exception {
        System.out.println("=== 启动TCP对比测试服务器 ===");
        
        // 初始化RPC配置
        com.zheng.RpcApplication.init();
        
        // 注册服务实现
        LocalRegistry.register(UserService.class.getName(), TestUserServiceImpl.class);
        
        // 启动TCP服务器
        VertxTcpServer tcpServer = new VertxTcpServer();
        tcpServer.doStart(TCP_PORT);
        System.out.println("TCP服务器已启动在端口" + TCP_PORT);
        
        // 等待服务器完全启动
        Thread.sleep(3000);
        System.out.println("TCP服务器启动完成\n");
    }

    @Test
    public void compareClientPerformance() throws Exception {
        System.out.println("=== TCP客户端性能对比测试 ===");
        
        final int THREAD_COUNT = 20;
        final int REQUESTS_PER_THREAD = 50;
        
        // 预热
        warmupBothClients();
        
        // 测试原始TCP客户端
        System.out.println("1. 测试原始TCP客户端（每次新建连接）");
        TestResult originalResult = testClient("原始TCP客户端", THREAD_COUNT, REQUESTS_PER_THREAD, false);
        
        // 稍微等待，让服务器恢复
        Thread.sleep(2000);
        
        // 测试优化TCP客户端
        System.out.println("2. 测试优化TCP客户端（连接池复用）");
        TestResult optimizedResult = testClient("优化TCP客户端", THREAD_COUNT, REQUESTS_PER_THREAD, true);
        
        // 对比分析
        compareResults(originalResult, optimizedResult);
        
        // 清理资源
        OptimizedTcpClient.cleanup();
    }

    private void warmupBothClients() throws Exception {
        System.out.println("预热两种客户端...");
        
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();
        
        // 预热原始客户端
        for (int i = 0; i < 10; i++) {
            try {
                VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);
            } catch (Exception e) {
                // 忽略预热错误
            }
        }
        
        // 预热优化客户端
        for (int i = 0; i < 10; i++) {
            try {
                OptimizedTcpClient.doRequest(rpcRequest, serviceMetaInfo);
            } catch (Exception e) {
                // 忽略预热错误
            }
        }
        
        System.out.println("预热完成\n");
    }

    private TestResult testClient(String clientName, int threadCount, int requestsPerThread, boolean useOptimized) throws Exception {
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong maxResponseTime = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        // 启动所有线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 错开启动时间
                    Thread.sleep(threadId * 2);
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        long requestStart = System.nanoTime();
                        try {
                            if (useOptimized) {
                                OptimizedTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                            } else {
                                VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                            }
                            
                            long requestEnd = System.nanoTime();
                            long responseTime = (requestEnd - requestStart) / 1_000_000; // 转换为毫秒
                            
                            totalResponseTime.addAndGet(responseTime);
                            successCount.incrementAndGet();
                            
                            // 更新最小和最大响应时间
                            updateMin(minResponseTime, responseTime);
                            updateMax(maxResponseTime, responseTime);
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            if (errorCount.get() <= 3) {
                                System.err.printf("%s请求失败: %s\n", clientName, e.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        if (!completed) {
            System.err.println(clientName + " 测试超时");
            executor.shutdownNow();
            return null;
        }
        
        // 计算统计数据
        int totalRequests = threadCount * requestsPerThread;
        long totalTime = endTime - startTime;
        double qps = totalTime > 0 ? (double) successCount.get() * 1000 / totalTime : 0;
        double avgResponseTime = successCount.get() > 0 ? (double) totalResponseTime.get() / successCount.get() : 0;
        double successRate = (double) successCount.get() / totalRequests * 100;
        
        TestResult result = new TestResult();
        result.clientName = clientName;
        result.totalRequests = totalRequests;
        result.successCount = successCount.get();
        result.errorCount = errorCount.get();
        result.successRate = successRate;
        result.totalTime = totalTime;
        result.qps = qps;
        result.avgResponseTime = avgResponseTime;
        result.minResponseTime = minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get();
        result.maxResponseTime = maxResponseTime.get();
        
        // 输出结果
        printTestResult(result);
        
        // 如果是优化客户端，打印连接池状态
        if (useOptimized) {
            TcpConnectionPool.printPoolStats();
        }
        
        executor.shutdown();
        return result;
    }

    private void updateMin(AtomicLong minValue, long newValue) {
        long current;
        do {
            current = minValue.get();
        } while (newValue < current && !minValue.compareAndSet(current, newValue));
    }

    private void updateMax(AtomicLong maxValue, long newValue) {
        long current;
        do {
            current = maxValue.get();
        } while (newValue > current && !maxValue.compareAndSet(current, newValue));
    }

    private void printTestResult(TestResult result) {
        System.out.printf("%s 测试结果:\n", result.clientName);
        System.out.printf("  总请求数: %d\n", result.totalRequests);
        System.out.printf("  成功请求: %d\n", result.successCount);
        System.out.printf("  失败请求: %d\n", result.errorCount);
        System.out.printf("  成功率: %.2f%%\n", result.successRate);
        System.out.printf("  总耗时: %dms\n", result.totalTime);
        System.out.printf("  QPS: %.2f\n", result.qps);
        System.out.printf("  平均响应时间: %.2fms\n", result.avgResponseTime);
        System.out.printf("  最小响应时间: %dms\n", result.minResponseTime);
        System.out.printf("  最大响应时间: %dms\n", result.maxResponseTime);
        System.out.println();
    }

    private void compareResults(TestResult original, TestResult optimized) {
        if (original == null || optimized == null) {
            System.out.println("无法进行对比，测试数据不完整");
            return;
        }
        
        System.out.println("=== 性能对比分析 ===");
        
        double qpsImprovement = ((optimized.qps - original.qps) / original.qps) * 100;
        double responseTimeImprovement = ((original.avgResponseTime - optimized.avgResponseTime) / original.avgResponseTime) * 100;
        double successRateImprovement = optimized.successRate - original.successRate;
        
        System.out.printf("QPS提升: %.2f%% (从 %.2f 提升到 %.2f)\n", 
                qpsImprovement, original.qps, optimized.qps);
        System.out.printf("响应时间改善: %.2f%% (从 %.2fms 降低到 %.2fms)\n", 
                responseTimeImprovement, original.avgResponseTime, optimized.avgResponseTime);
        System.out.printf("成功率变化: %.2f%% (从 %.2f%% 到 %.2f%%)\n", 
                successRateImprovement, original.successRate, optimized.successRate);
        
        System.out.println("\n=== 结论 ===");
        if (qpsImprovement > 0) {
            System.out.printf("✅ 优化TCP客户端性能显著提升，QPS提升了 %.1f倍\n", qpsImprovement / 100 + 1);
        } else {
            System.out.println("❌ 优化TCP客户端性能未达到预期");
        }
        
        System.out.println("\n=== 性能提升原因 ===");
        System.out.println("1. 连接复用：避免了TCP三次握手和四次挥手的开销");
        System.out.println("2. 资源复用：共享Vertx实例，减少对象创建销毁");
        System.out.println("3. 连接池管理：智能的连接生命周期管理");
        System.out.println("4. 异步处理：更高效的异步请求处理机制");
    }

    private static class TestResult {
        String clientName;
        int totalRequests;
        int successCount;
        int errorCount;
        double successRate;
        long totalTime;
        double qps;
        double avgResponseTime;
        long minResponseTime;
        long maxResponseTime;
    }

    private RpcRequest createTestRequest() {
        User user = new User();
        user.setName("comparisonTestUser");
        
        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setServiceName("com.zheng.service.UserService");
        rpcRequest.setMethodName("getUser");
        rpcRequest.setParameterTypes(new Class[]{User.class});
        rpcRequest.setArgs(new Object[]{user});
        
        return rpcRequest;
    }

    private ServiceMetaInfo createServiceMetaInfo() {
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceHost(TCP_HOST);
        serviceMetaInfo.setServicePort(TCP_PORT);
        return serviceMetaInfo;
    }
}
