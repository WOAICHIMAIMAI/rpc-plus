package com.zheng.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;
import cn.hutool.setting.yaml.YamlUtil;

/**
 * 配置工具类
 */
public class ConfigUtils {

    private static String[] extensions = new String[]{".properties", ".yaml", ".yml"};

    private static final String APPLICATION = "application";

    /**
     * 加载配置对象
     *
     * @param tClass
     * @param prefix
     * @param <T>
     * @return
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix) {
        return loadConfig(tClass, prefix, "");
    }

    /**
     * 加载配置对象，支持区分环境
     *
     * @param tClass
     * @param prefix
     * @param environment
     * @param <T>
     * @return
     */
    public static <T> T loadConfig2(Class<T> tClass, String prefix, String environment) {
        StringBuilder configFileBuilder = new StringBuilder("application");
        if (StrUtil.isNotBlank(environment)) {
            configFileBuilder.append("-").append(environment);
        }
        configFileBuilder.append(".properties");
        Props props = new Props(configFileBuilder.toString());
        return props.toBean(tClass, prefix);
    }

    public static <T> T loadConfig(Class<T> tClass, String prefix, String environment){
        StringBuilder configFileBuilder = new StringBuilder(APPLICATION);
        if(StrUtil.isNotEmpty(environment)){
            configFileBuilder.append("-").append(environment);
        }
        String baseFileName = configFileBuilder.toString();
        for (String extension : extensions) {
            String path = baseFileName + extension;
            if(!isExist(path)) continue;
            return switch (extension) {
                case ".properties" -> {
                    Props props = new Props(path);
                    yield props.toBean(tClass, prefix);
                }
                case ".yaml" -> {
                    Dict dictYaml = YamlUtil.loadByPath(path);
                    yield BeanUtil.copyProperties(dictYaml.getBean(prefix), tClass);
                }
                case ".yml" -> {
                    Dict dictYml = YamlUtil.loadByPath(path);
                    yield BeanUtil.copyProperties(dictYml.getBean(prefix), tClass);
                }
                default -> throw new RuntimeException("文件不存在");
            };
        }
        return null;
    }

    /**
     * 通过文件路径判断某一文件是否存在
     * @param path 文件路径
     * @return 是否存在某一文件
     */
    private static boolean isExist(String path){
        // 获取当前线程的上下文加载器
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return ObjectUtil.isNotNull(classLoader.getResource(path));
    }
}
