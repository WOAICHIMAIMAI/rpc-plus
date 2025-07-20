package com.zheng.examplespringbootconsumer;


import com.zheng.model.User;
import com.zheng.service.UserService;
import com.zheng.zzrpcspringbootstarter.annotation.RpcReference;
import org.springframework.stereotype.Service;

@Service
public class ExampleServiceImpl {

    @RpcReference
    private UserService userService;

    public void test() {
        User user = new User();
        user.setName("yupi");
        User resultUser = userService.getUser(user);
        System.out.println(resultUser.getName());
    }

}
