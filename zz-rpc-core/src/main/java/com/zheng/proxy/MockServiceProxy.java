package com.zheng.proxy;

import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Locale;

@Slf4j
public class MockServiceProxy implements InvocationHandler {

    /**
     * 调用代理
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> methodReturn = method.getReturnType();
        log.info("mock invoke {}", method.getName());
        return getDefaultObject(methodReturn);
    }

    /**
     * 生成指定类型的默认值对象（可自行完善默认值逻辑）
     * @param type
     * @return
     */
    private Object getDefaultObject(Class<?> type) {
        // isPrimitive()判断是否为基本类型
        if(type.isPrimitive()){
            if(type == boolean.class){
                return false;
            }else if(type == int.class){
                return 0;
            }else if(type == short.class){
                return (short) 0;
            }else if(type == long.class){
                return 0L;
            }else if(type == float.class){
                return 0f;
            }else if(type == double.class){
                return 0D;
            }else if(type == char.class){
                return (char)0;
            }
        }
        return null;
    }
}
