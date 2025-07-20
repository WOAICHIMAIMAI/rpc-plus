package com.zheng.tolerant;

import com.zheng.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 降级到其他服务 - 容错策略
 */
@Slf4j
public class FailBackTolerantStrategy implements TolerantStrategy {

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        log.warn("服务调用失败，启动降级策略", e);
        
        // 创建降级响应
        RpcResponse rpcResponse = new RpcResponse();
        
        // 可以从 context 中获取降级服务信息
        // 这里提供一个默认的降级实现
        try {
            // 获取降级服务地址（可以从配置或context中获取）
            String fallbackService = getFallbackService(context);
            
            if (fallbackService != null) {
                log.info("尝试调用降级服务: {}", fallbackService);
                // 这里可以调用降级服务
                // 为了简化，返回一个默认的降级结果
                rpcResponse.setData(getDefaultFallbackResult(context));
                rpcResponse.setMessage("降级服务调用成功");
            } else {
                // 没有配置降级服务，返回默认值
                rpcResponse.setData(getDefaultFallbackResult(context));
                rpcResponse.setMessage("使用默认降级结果");
            }
            
        } catch (Exception fallbackException) {
            log.error("降级服务调用也失败了", fallbackException);
            // 降级服务也失败，返回空结果
            rpcResponse.setData(null);
            rpcResponse.setMessage("降级服务调用失败");
        }
        
        return rpcResponse;
    }
    
    /**
     * 获取降级服务地址
     */
    private String getFallbackService(Map<String, Object> context) {
        if (context != null) {
            return (String) context.get("fallbackService");
        }
        return null;
    }
    
    /**
     * 获取默认降级结果
     */
    private Object getDefaultFallbackResult(Map<String, Object> context) {
        if (context != null) {
            // 可以根据方法返回类型返回不同的默认值
            String methodName = (String) context.get("methodName");
            if (methodName != null) {
                switch (methodName) {
                    case "getUser":
                        // 返回一个默认用户对象
                        return createDefaultUser();
                    case "getUserList":
                        // 返回空列表
                        return java.util.Collections.emptyList();
                    default:
                        return null;
                }
            }
        }
        return null;
    }
    
    /**
     * 创建默认用户对象（示例）
     */
    private Object createDefaultUser() {
        // 这里可以返回一个默认的用户对象
        // 具体实现取决于你的User类结构
        try {
            // 使用反射创建默认对象，或者直接new一个
            return new Object(); // 这里应该是具体的User对象
        } catch (Exception e) {
            log.warn("创建默认用户对象失败", e);
            return null;
        }
    }
}
