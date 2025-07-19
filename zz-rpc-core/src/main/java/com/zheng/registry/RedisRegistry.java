package com.zheng.registry;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.json.JSONUtil;
import com.zheng.model.ServiceMetaInfo;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RedisRegistry implements Registry {

    private JedisPool jedisPool;

    /**
     * 根节点
     */
    private static final String REDIS_ROOT_PATH = "rpc:";

    /**
     * 本机注册的节点 key 集合（用于维护续期）
     */
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    /**
     * 注册中心服务缓存
     */
    private final RegistryServiceCache registryServiceCache = new RegistryServiceCache();

    /**
     * 正在监听的 key 集合
     */
    private final Set<String> watchingKeySet = new ConcurrentHashSet<>();

    @Override
    public void init(RegistryConfig registryConfig) {
        // 解析 Redis 地址
        String address = registryConfig.getAddress();
        // 设置 host 和 port 为默认值
        String host = "localhost";
        int port = 6379;
        // http://localhost:2379 或者 localhost:2379 或者 127.0.0.1:2379 需要特判
        if(address != null && !address.isEmpty()){
            // 移除协议前缀
            if(address.startsWith("http://")){
                address = address.substring(7);
            }else if(address.startsWith("https://")){
                address = address.substring(8);
            }
        }
        // 判断输入格式是否为 host:port ,如果不包含port就用默认
        if(address.contains(":")){
            String[] parts = address.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        }

        // 创建连接池配置
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);

        // 创建连接池
        jedisPool = new JedisPool(poolConfig, host, port, 
                Math.toIntExact(registryConfig.getTimeout()));

        heartBeat();
    }

    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            String registerKey = REDIS_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
            String value = JSONUtil.toJsonStr(serviceMetaInfo);
            
            // 设置键值对，TTL 为 30 秒
            jedis.setex(registerKey, 30, value);
            
            // 添加到本地缓存
            localRegisterNodeKeySet.add(registerKey);
        }
    }

    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        try (Jedis jedis = jedisPool.getResource()) {
            String registerKey = REDIS_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
            jedis.del(registerKey);
            
            // 从本地缓存移除
            localRegisterNodeKeySet.remove(registerKey);
        }
    }

    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从缓存获取服务
        List<ServiceMetaInfo> cachedServiceMetaInfoList = registryServiceCache.readCache();
        if (cachedServiceMetaInfoList != null) {
            return cachedServiceMetaInfoList;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            // 使用模式匹配查找服务
            String searchPattern = REDIS_ROOT_PATH + serviceKey + ":*";
            Set<String> keys = jedis.keys(searchPattern);
            
            if (CollUtil.isEmpty(keys)) {
                return List.of();
            }

            // 批量获取值
            List<String> values = jedis.mget(keys.toArray(new String[0]));
            
            // 解析服务信息
            List<ServiceMetaInfo> serviceMetaInfoList = values.stream()
                    .filter(value -> value != null)
                    .map(value -> JSONUtil.toBean(value, ServiceMetaInfo.class))
                    .collect(Collectors.toList());

            // 写入服务缓存
            registryServiceCache.writeCache(serviceMetaInfoList);
            return serviceMetaInfoList;
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    @Override
    public void heartBeat() {
        // 10 秒续签一次
        CronUtil.schedule("*/10 * * * * *", new Task() {
            @Override
            public void execute() {
                for (String key : localRegisterNodeKeySet) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        String value = jedis.get(key);
                        if (value == null) {
                            continue;
                        }
                        
                        // 重新设置 TTL（续签）
                        jedis.expire(key, 30);
                    } catch (Exception e) {
                        log.error(key + "续签失败", e);
                    }
                }
            }
        });

        CronUtil.setMatchSecond(true);
        CronUtil.start();
    }

    @Override
    public void watch(String serviceNodeKey) {
        // Redis 的 keyspace notifications 需要服务器配置支持
        // 这里简化实现，实际生产环境可以使用 Redis Streams 或其他方案
        boolean newWatch = watchingKeySet.add(serviceNodeKey);
        if (newWatch) {
            // 启动一个线程监听 key 的变化
            new Thread(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.psubscribe(new JedisPubSub() {
                        @Override
                        public void onPMessage(String pattern, String channel, String message) {
                            if (channel.contains("del") || channel.contains("expired")) {
                                registryServiceCache.clearCache();
                            }
                        }
                    }, "__keyspace@0__:" + serviceNodeKey);
                } catch (Exception e) {
                    log.error("监听失败", e);
                }
            }).start();
        }
    }

    @Override
    public void destroy() {
        log.info("当前节点下线");
        
        // 删除本地注册的所有节点
        try (Jedis jedis = jedisPool.getResource()) {
            for (String key : localRegisterNodeKeySet) {
                jedis.del(key);
            }
        } catch (Exception e) {
            log.error("节点下线失败", e);
        }

        // 关闭连接池
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}