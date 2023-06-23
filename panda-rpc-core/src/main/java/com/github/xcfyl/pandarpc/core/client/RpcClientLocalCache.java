package com.github.xcfyl.pandarpc.core.client;

import com.github.xcfyl.pandarpc.core.protocol.RpcResponse;
import com.github.xcfyl.pandarpc.core.registry.RegistryData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * rpc客户端本地的缓存
 *
 * @author 西城风雨楼
 * @date create at 2023/6/22 12:24
 */
public class RpcClientLocalCache {
    /**
     * 缓存rpc调用结果，key是请求id，value是本次请求的响应数据
     */
    public static final Map<String, RpcResponse> RESPONSE_MAP = new ConcurrentHashMap<>();

    public static final Map<String, RegistryData> REGISTRY_DATA_CACHE = new HashMap<>();
}