package com.zheng;

import com.zheng.model.RpcRequest;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.model.User;
import com.zheng.registry.LocalRegistry;
import com.zheng.server.tcp.VertxTcpClient;
import com.zheng.server.tcp.VertxTcpServer;
import com.zheng.service.UserService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单TCP压力测试 - 避免连接池并发问题
 */
public class SimpleTcpStressTest {

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
        System.out.println("=== 启动简单TCP测试服务器 ===");
        
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
    public void lightStressTest() throws Exception {
        System.out.println("=== 轻量级TCP压力测试 ===");
        executeStressTest(10, 20, "轻量级");
    }

    @Test
    public void mediumStressTest() throws Exception {
        System.out.println("=== 中等TCP压力测试 ===");
        executeStressTest(20, 50, "中等");
    }

    @Test
    public void heavyStressTest() throws Exception {
        System.out.println("=== 重量级TCP压力测试 ===");
        executeStressTest(50, 100, "重量级");
    }

    private void executeStressTest(int threadCount, int requestsPerThread, String testType) throws Exception {
        System.out.printf("%s压力测试 - %d个线程，每线程%d个请求\n", testType, threadCount, requestsPerThread);
        
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
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // 每个线程稍微错开启动时间
                    Thread.sleep(threadId * 5);
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        long requestStart = System.nanoTime();
                        try {
                            VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                            long requestEnd = System.nanoTime();
                            totalResponseTime.addAndGet((requestEnd - requestStart) / 1_000_000);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            // 只打印前3个错误，避免日志过多
                            if (errorCount.get() <= 3) {
                                System.err.printf("线程%d请求失败: %s\n", threadId, e.getMessage());
                            }
                        }
                        
                        // 在请求之间稍微暂停，减少服务器压力
                        if (j % 10 == 0) {
                            Thread.sleep(1);
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
            System.err.println("测试超时，强制结束");
            executor.shutdownNow();
            return;
        }
        
        // 计算统计数据
        int totalRequests = threadCount * requestsPerThread;
        long totalTime = endTime - startTime;
        double qps = totalTime > 0 ? (double) successCount.get() * 1000 / totalTime : 0;
        double avgResponseTime = successCount.get() > 0 ? (double) totalResponseTime.get() / successCount.get() : 0;
        double successRate = (double) successCount.get() / totalRequests * 100;
        
        // 输出结果
        System.out.printf("结果统计:\n");
        System.out.printf("  总请求数: %d\n", totalRequests);
        System.out.printf("  成功请求: %d\n", successCount.get());
        System.out.printf("  失败请求: %d\n", errorCount.get());
        System.out.printf("  成功率: %.2f%%\n", successRate);
        System.out.printf("  总耗时: %dms\n", totalTime);
        System.out.printf("  QPS: %.2f\n", qps);
        System.out.printf("  平均响应时间: %.2fms\n", avgResponseTime);
        System.out.println();
        
        executor.shutdown();
    }

    @Test
    public void sequentialTest() throws Exception {
        System.out.println("=== 顺序执行测试（基准测试） ===");
        
        RpcRequest rpcRequest = createTestRequest();
        ServiceMetaInfo serviceMetaInfo = createServiceMetaInfo();
        
        final int REQUEST_COUNT = 100;
        AtomicLong totalResponseTime = new AtomicLong(0);
        int successCount = 0;
        int errorCount = 0;
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < REQUEST_COUNT; i++) {
            long requestStart = System.nanoTime();
            try {
                VertxTcpClient.doRequest(rpcRequest, serviceMetaInfo);
                long requestEnd = System.nanoTime();
                totalResponseTime.addAndGet((requestEnd - requestStart) / 1_000_000);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                if (errorCount <= 3) {
                    System.err.println("请求失败: " + e.getMessage());
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double qps = totalTime > 0 ? (double) successCount * 1000 / totalTime : 0;
        double avgResponseTime = successCount > 0 ? (double) totalResponseTime.get() / successCount : 0;
        double successRate = (double) successCount / REQUEST_COUNT * 100;
        
        System.out.printf("顺序执行结果:\n");
        System.out.printf("  总请求数: %d\n", REQUEST_COUNT);
        System.out.printf("  成功请求: %d\n", successCount);
        System.out.printf("  失败请求: %d\n", errorCount);
        System.out.printf("  成功率: %.2f%%\n", successRate);
        System.out.printf("  总耗时: %dms\n", totalTime);
        System.out.printf("  QPS: %.2f\n", qps);
        System.out.printf("  平均响应时间: %.2fms\n", avgResponseTime);
        System.out.println();
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
