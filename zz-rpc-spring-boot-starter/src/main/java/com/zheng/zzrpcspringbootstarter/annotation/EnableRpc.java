package com.zheng.zzrpcspringbootstarter.annotation;

import com.zheng.zzrpcspringbootstarter.bootstrap.RpcConsumerBootstrap;
import com.zheng.zzrpcspringbootstarter.bootstrap.RpcInitBootstrap;
import com.zheng.zzrpcspringbootstarter.bootstrap.RpcProviderBootstrap;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({RpcInitBootstrap.class, RpcProviderBootstrap.class, RpcConsumerBootstrap.class})
public @interface EnableRpc {

    /**
     * 需要启动 server
     *
     * @return
     */
    boolean needServer() default true;
}
