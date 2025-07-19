package com.zheng.provider;

import cn.hutool.core.net.NetUtil;
import com.zheng.RpcApplication;
import com.zheng.conf.RpcConfig;
import com.zheng.model.ServiceMetaInfo;
import com.zheng.registry.LocalRegistry;
import com.zheng.registry.Registry;
import com.zheng.registry.RegistryConfig;
import com.zheng.registry.RegistryFactory;
import com.zheng.server.tcp.VertxTcpServer;
import com.zheng.service.UserService;

/**
 * 服务提供者示例
 */
public class ProviderExample {

    public static void main(String[] args) {
        // RPC 框架初始化
        RpcApplication.init();

        // 注册服务
        String serviceName = UserService.class.getName();
        LocalRegistry.register(serviceName, UserServiceImpl.class);

        // 注册服务到注册中心
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 启动 TCP 服务
        VertxTcpServer vertxTcpServer = new VertxTcpServer();
        vertxTcpServer.doStart(8080);
    }
}
