package com.zheng.loadbalancer;

import com.zheng.model.ServiceMetaInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 最少活跃数负载均衡器
 */
public class LeastActiveLoadBalancer implements LoadBalancer {

    /**
     * 活跃请求数统计，key 为服务地址，value 为活跃请求数
     */
    private final Map<String, AtomicInteger> activeCountMap = new ConcurrentHashMap<>();

    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        if (serviceMetaInfoList.isEmpty()) {
            return null;
        }

        // 只有一个服务，直接返回
        if (serviceMetaInfoList.size() == 1) {
            ServiceMetaInfo serviceMetaInfo = serviceMetaInfoList.get(0);
            incrementActive(serviceMetaInfo);
            return serviceMetaInfo;
        }

        // 找到活跃数最少的服务
        ServiceMetaInfo leastActiveService = null;
        int leastActive = Integer.MAX_VALUE;

        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            String serviceKey = getServiceKey(serviceMetaInfo);
            int activeCount = activeCountMap.computeIfAbsent(serviceKey, k -> new AtomicInteger(0)).get();
            
            if (activeCount < leastActive) {
                leastActive = activeCount;
                leastActiveService = serviceMetaInfo;
            }
        }

        // 增加选中服务的活跃数
        if (leastActiveService != null) {
            incrementActive(leastActiveService);
        }

        return leastActiveService;
    }

    /**
     * 增加活跃请求数
     */
    private void incrementActive(ServiceMetaInfo serviceMetaInfo) {
        String serviceKey = getServiceKey(serviceMetaInfo);
        activeCountMap.computeIfAbsent(serviceKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 减少活跃请求数（请求完成后调用）
     */
    public void decrementActive(ServiceMetaInfo serviceMetaInfo) {
        String serviceKey = getServiceKey(serviceMetaInfo);
        AtomicInteger activeCount = activeCountMap.get(serviceKey);
        if (activeCount != null) {
            activeCount.decrementAndGet();
            // 防止负数
            if (activeCount.get() < 0) {
                activeCount.set(0);
            }
        }
    }

    /**
     * 获取服务唯一标识
     */
    private String getServiceKey(ServiceMetaInfo serviceMetaInfo) {
        return serviceMetaInfo.getServiceAddress();
    }

    /**
     * 获取服务活跃数（用于监控）
     */
    public int getActiveCount(ServiceMetaInfo serviceMetaInfo) {
        String serviceKey = getServiceKey(serviceMetaInfo);
        AtomicInteger activeCount = activeCountMap.get(serviceKey);
        return activeCount != null ? activeCount.get() : 0;
    }

    /**
     * 清理不存在的服务统计信息
     */
    public void cleanup(List<ServiceMetaInfo> currentServices) {
        // 获取当前所有服务的 key
        Set<String> currentServiceKeys = currentServices.stream()
                .map(this::getServiceKey)
                .collect(Collectors.toSet());
        
        // 移除不存在的服务统计
        activeCountMap.entrySet().removeIf(entry -> !currentServiceKeys.contains(entry.getKey()));
    }
}