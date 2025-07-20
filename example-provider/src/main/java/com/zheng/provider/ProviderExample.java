package com.zheng.provider;



import com.zheng.bootstrap.ProviderBootstrap;
import com.zheng.model.ServiceRegisterInfo;
import com.zheng.service.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务提供者示例
 *

 */
public class ProviderExample {

    public static void main(String[] args) {
        // 要注册的服务
        List<ServiceRegisterInfo<?>> serviceRegisterInfoList = new ArrayList<>();
        ServiceRegisterInfo serviceRegisterInfo = new ServiceRegisterInfo(UserService.class.getName(), UserServiceImpl.class);
        serviceRegisterInfoList.add(serviceRegisterInfo);

        // 服务提供者初始化
        ProviderBootstrap.init(serviceRegisterInfoList);
    }
}
