package com.zheng.provider;

import com.zheng.RpcApplication;
import com.zheng.registry.LocalRegistry;
import com.zheng.server.HttpServer;
import com.zheng.server.VertxHttpServer;
import com.zheng.service.UserService;

/**
 * 简易服务提供者示例
 *
 */
public class EasyProviderExample {

    public static void main(String[] args) {
        // RPC 框架初始化
        RpcApplication.init();
        
        // 注册服务
        LocalRegistry.register(UserService.class.getName(), UserServiceImpl.class);

        // 启动 web 服务
        HttpServer httpServer = new VertxHttpServer();
        httpServer.doStart(RpcApplication.getRpcConfig().getServerPort());
    }
}
