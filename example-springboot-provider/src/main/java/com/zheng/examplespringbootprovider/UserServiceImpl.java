package com.zheng.examplespringbootprovider;


import com.zheng.model.User;
import com.zheng.service.UserService;
import com.zheng.zzrpcspringbootstarter.annotation.RpcService;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 */
@Service
@RpcService
public class UserServiceImpl implements UserService {
    public User getUser(User user) {
        System.out.println("用户名：" + user.getName());
        return user;
    }
}
