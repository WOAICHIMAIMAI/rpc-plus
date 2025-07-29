package com.zheng;

import com.zheng.model.User;
import com.zheng.proxy.ServiceProxyFactory;
import com.zheng.registry.LocalRegistry;
import com.zheng.service.UserService;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest(String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }

    public void test()
    {
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("zhengjiajun");
        // 调用
        long start = System.currentTimeMillis();
        for(int i = 0; i < 1000; i++){
            User newUser = userService.getUser(user);
            if (newUser != null) {
                System.out.println(newUser.getName());
            } else {
                System.out.println("user == null");
            }
        }
        System.out.println(System.currentTimeMillis() - start);
    }

    public void test2()
    {
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("zhengjiajun");
        // 调用
        long start = System.currentTimeMillis();
        User newUser = userService.getUser(user);
        System.out.println(System.currentTimeMillis() - start);
    }

}
