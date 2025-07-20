package com.zheng.tolerant;

import com.zheng.model.RpcRequest;
import com.zheng.model.RpcResponse;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.server.tcp.VertxTcpClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 转移到其他服务节点 - 容错策略
 */
@Slf4j
public class FailOverTolerantStrategy implements TolerantStrategy {

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        log.warn("服务调用失败，启动故障转移策略", e);
        
        // 从上下文中获取其他可用的服务节点
        List<ServiceMetaInfo> serviceMetaInfoList = getAvailableServices(context);
        ServiceMetaInfo failedService = getCurrentService(context);
        
        if (serviceMetaInfoList == null || serviceMetaInfoList.isEmpty()) {
            log.error("没有可用的备用服务节点");
            return createFailResponse("没有可用的备用服务节点");
        }
        
        // 尝试调用其他服务节点
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            // 跳过已经失败的服务节点
            if (failedService != null && serviceMetaInfo.equals(failedService)) {
                continue;
            }
            
            try {
                log.info("尝试转移到服务节点: {}", serviceMetaInfo.getServiceAddress());
                
                // 从上下文获取原始请求
                Object request = context.get("request");
                if (request != null) {
                    // 调用备用服务节点
                    RpcResponse response = VertxTcpClient.doRequest((RpcRequest) request, serviceMetaInfo);
                    if (response != null) {
                        log.info("故障转移成功，使用服务节点: {}", serviceMetaInfo.getServiceAddress());
                        response.setMessage("故障转移调用成功");
                        return response;
                    }
                }
            } catch (Exception failoverException) {
                log.warn("服务节点 {} 调用失败，尝试下一个节点", serviceMetaInfo.getServiceAddress(), failoverException);
            }
        }
        
        log.error("所有备用服务节点都调用失败");
        return createFailResponse("所有备用服务节点都调用失败");
    }
    
    /**
     * 从上下文中获取可用的服务节点列表
     */
    @SuppressWarnings("unchecked")
    private List<ServiceMetaInfo> getAvailableServices(Map<String, Object> context) {
        if (context != null) {
            return (List<ServiceMetaInfo>) context.get("serviceMetaInfoList");
        }
        return null;
    }
    
    /**
     * 从上下文中获取当前失败的服务节点
     */
    private ServiceMetaInfo getCurrentService(Map<String, Object> context) {
        if (context != null) {
            return (ServiceMetaInfo) context.get("currentService");
        }
        return null;
    }
    
    /**
     * 创建失败响应
     */
    private RpcResponse createFailResponse(String message) {
        RpcResponse response = new RpcResponse();
        response.setMessage(message);
        response.setData(null);
        return response;
    }
}
