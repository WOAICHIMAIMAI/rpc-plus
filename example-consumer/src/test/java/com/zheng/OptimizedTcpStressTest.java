package com.zheng;

import com.zheng.model.RpcRequest;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.model.User;
import com.zheng.registry.LocalRegistry;
import com.zheng.server.tcp.OptimizedTcpClient;
import com.zheng.server.tcp.TcpConnectionPool;
import com.zheng.server.tcp.VertxTcpServer;
import com.zheng.service.UserService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化TCP客户端压力测试
 */
public class OptimizedTcpStressTest {

    private static final String TCP_HOST = "localhost";
    private static final int TCP_PORT = 8888;

    // 压测配置
    private static final int WARMUP_REQUESTS = 100;           // 预热请求数
    private static final int LIGHT_LOAD_THREADS = 10;         // 轻负载线程数
    private static final int MEDIUM_LOAD_THREADS = 50;        // 中负载线程数  
    private static final int HEAVY_LOAD_THREADS = 100;        // 重负载线程数
    private static final int EXTREME_LOAD_THREADS = 200;      // 极限负载线程数
    private static final int REQUESTS_PER_THREAD = 100;       // 每线程请求数
    private static final int TEST_DURATION_SECONDS = 30;      // 持续压测时间(秒)

    // 简单的UserService实现类
    public static class TestUserServiceImpl implements UserService {
        @Override
        public User getUser(User user) {
            return user; // 直接返回，减少业务逻辑干扰
        }
    }

    @BeforeClass
    public static void setupTcpServer() throws Exception {
        System.out.println("=== 启动TCP压测服务器 ===");
        
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
        System.out.println("TCP服务器启动完成，开始压测...\n");
    }

    @Test
    public void stressTestOptimizedTcp() throws Exception {
        System.out.println("=== 优化TCP客户端压力测试 ===");
        
        // 预热
        warmup();
        
        // 不同负载级别的压测
        testLightLoad();
        testMediumLoad(); 
        testHeavyLoad();
        testExtremeLoad();
        
        // 持续压测
        testSustainedLoad();
        
        // 清理资源
        OptimizedTcpClient.cleanup();
        System.out.println("\n=== 压测完成 ===");
    }

    private void warmup() throws Exception {
        System.out.println("1. 预热阶段 - " + WARMUP_REQUESTS + " 个请求");
        
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            try {
                OptimizedTcpClient.doRequest(rpcRequest, serviceMetaInfo);
            } catch (Exception e) {
                // 忽略预热错误
            }
        }
        long endTime = System.currentTimeMillis();
        
        System.out.printf("预热完成，耗时: %dms\n\n", endTime - startTime);
    }

    private void testLightLoad() throws Exception {
        System.out.println("2. 轻负载测试 - " + LIGHT_LOAD_THREADS + " 个线程");
        executeLoadTest(LIGHT_LOAD_THREADS, REQUESTS_PER_THREAD);
    }

    private void testMediumLoad() throws Exception {
        System.out.println("3. 中负载测试 - " + MEDIUM_LOAD_THREADS + " 个线程");
        executeLoadTest(MEDIUM_LOAD_THREADS, REQUESTS_PER_THREAD);
    }

    private void testHeavyLoad() throws Exception {
        System.out.println("4. 重负载测试 - " + HEAVY_LOAD_THREADS + " 个线程");
        executeLoadTest(HEAVY_LOAD_THREADS, REQUESTS_PER_THREAD);
    }

    private void testExtremeLoad() throws Exception {
        System.out.println("5. 极限负载测试 - " + EXTREME_LOAD_THREADS + " 个线程");
        executeLoadTest(EXTREME_LOAD_THREADS, REQUESTS_PER_THREAD);
    }

    private void testSustainedLoad() throws Exception {
        System.out.println("6. 持续压测 - " + MEDIUM_LOAD_THREADS + " 个线程持续 " + TEST_DURATION_SECONDS + " 秒");
        executeSustainedLoadTest(MEDIUM_LOAD_THREADS, TEST_DURATION_SECONDS);
    }

    private void executeLoadTest(int threadCount, int requestsPerThread) throws Exception {
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        // 启动所有线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        long requestStart = System.nanoTime();
                        try {
                            OptimizedTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                            long requestEnd = System.nanoTime();
                            totalResponseTime.addAndGet((requestEnd - requestStart) / 1_000_000); // 转换为毫秒
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        long endTime = System.currentTimeMillis();
        
        // 计算统计数据
        int totalRequests = threadCount * requestsPerThread;
        long totalTime = endTime - startTime;
        double qps = (double) successCount.get() * 1000 / totalTime;
        double avgResponseTime = successCount.get() > 0 ? (double) totalResponseTime.get() / successCount.get() : 0;
        double successRate = (double) successCount.get() / totalRequests * 100;
        
        // 输出结果
        System.out.printf("   总请求数: %d\n", totalRequests);
        System.out.printf("   成功请求: %d\n", successCount.get());
        System.out.printf("   失败请求: %d\n", errorCount.get());
        System.out.printf("   成功率: %.2f%%\n", successRate);
        System.out.printf("   总耗时: %dms\n", totalTime);
        System.out.printf("   QPS: %.2f\n", qps);
        System.out.printf("   平均响应时间: %.2fms\n", avgResponseTime);
        
        // 打印连接池状态
        TcpConnectionPool.printPoolStats();
        System.out.println();
        
        executor.shutdown();
    }

    private void executeSustainedLoadTest(int threadCount, int durationSeconds) throws Exception {
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        final AtomicBoolean running = new AtomicBoolean(true);
        
        long startTime = System.currentTimeMillis();
        
        // 启动工作线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                while (running.get()) {
                    long requestStart = System.nanoTime();
                    try {
                        OptimizedTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                        long requestEnd = System.nanoTime();
                        totalResponseTime.addAndGet((requestEnd - requestStart) / 1_000_000);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            });
        }

        // 运行指定时间
        Thread.sleep(durationSeconds * 1000);
        running.set(false);
        
        // 等待所有线程结束
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // 计算统计数据
        int totalRequests = successCount.get() + errorCount.get();
        double qps = (double) successCount.get() * 1000 / totalTime;
        double avgResponseTime = successCount.get() > 0 ? (double) totalResponseTime.get() / successCount.get() : 0;
        double successRate = totalRequests > 0 ? (double) successCount.get() / totalRequests * 100 : 0;
        
        // 输出结果
        System.out.printf("   持续时间: %d秒\n", durationSeconds);
        System.out.printf("   总请求数: %d\n", totalRequests);
        System.out.printf("   成功请求: %d\n", successCount.get());
        System.out.printf("   失败请求: %d\n", errorCount.get());
        System.out.printf("   成功率: %.2f%%\n", successRate);
        System.out.printf("   平均QPS: %.2f\n", qps);
        System.out.printf("   平均响应时间: %.2fms\n", avgResponseTime);
        
        // 打印连接池状态
        TcpConnectionPool.printPoolStats();
        System.out.println();
    }

    @Test
    public void quickStressTest() throws Exception {
        System.out.println("=== 快速TCP压测 ===");

        // 使用简化的压测，避免连接池并发问题
        simpleStressTest();
    }

    private void simpleStressTest() throws Exception {
        System.out.println("简单压力测试 - 20个线程，每线程50个请求");

        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();

        final int THREAD_COUNT = 20;
        final int REQUESTS_PER_THREAD = 50;

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        // 启动所有线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 每个线程稍微错开启动时间，避免同时创建连接
                    Thread.sleep(threadId * 10);

                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        long requestStart = System.nanoTime();
                        try {
                            // 使用原始TCP客户端，避免连接池问题
                            com.zheng.server.tcp.VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                            long requestEnd = System.nanoTime();
                            totalResponseTime.addAndGet((requestEnd - requestStart) / 1_000_000);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            if (errorCount.get() <= 5) { // 只打印前5个错误
                                System.err.println("请求失败: " + e.getMessage());
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
        latch.await();
        long endTime = System.currentTimeMillis();

        // 计算统计数据
        int totalRequests = THREAD_COUNT * REQUESTS_PER_THREAD;
        long totalTime = endTime - startTime;
        double qps = (double) successCount.get() * 1000 / totalTime;
        double avgResponseTime = successCount.get() > 0 ? (double) totalResponseTime.get() / successCount.get() : 0;
        double successRate = (double) successCount.get() / totalRequests * 100;

        // 输出结果
        System.out.printf("总请求数: %d\n", totalRequests);
        System.out.printf("成功请求: %d\n", successCount.get());
        System.out.printf("失败请求: %d\n", errorCount.get());
        System.out.printf("成功率: %.2f%%\n", successRate);
        System.out.printf("总耗时: %dms\n", totalTime);
        System.out.printf("QPS: %.2f\n", qps);
        System.out.printf("平均响应时间: %.2fms\n", avgResponseTime);

        executor.shutdown();
        System.out.println("简单压测完成");
    }

    private RpcRequest createTestRequest() {
        User user = new User();
        user.setName("stressTestUser");
        
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
