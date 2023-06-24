package com.github.xcfyl.pandarpc.core.server;

import com.github.xcfyl.pandarpc.core.common.config.RpcConfigLoader;
import com.github.xcfyl.pandarpc.core.common.config.RpcServerConfig;
import com.github.xcfyl.pandarpc.core.common.enums.RpcRegistryDataAttrName;
import com.github.xcfyl.pandarpc.core.common.enums.RpcRegistryType;
import com.github.xcfyl.pandarpc.core.common.enums.RpcSerializeType;
import com.github.xcfyl.pandarpc.core.common.utils.CommonUtils;
import com.github.xcfyl.pandarpc.core.exception.ConfigErrorException;
import com.github.xcfyl.pandarpc.core.protocol.RpcTransferProtocolDecoder;
import com.github.xcfyl.pandarpc.core.protocol.RpcTransferProtocolEncoder;
import com.github.xcfyl.pandarpc.core.registry.RegistryData;
import com.github.xcfyl.pandarpc.core.registry.RpcRegistry;
import com.github.xcfyl.pandarpc.core.registry.zookeeper.ZookeeperClient;
import com.github.xcfyl.pandarpc.core.registry.zookeeper.ZookeeperRegistry;
import com.github.xcfyl.pandarpc.core.serialize.fastjson.FastJsonRpcSerializeFactory;
import com.github.xcfyl.pandarpc.core.serialize.jdk.JdkRpcSerializeFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * rpc服务端
 *
 * @author 西城风雨楼
 * @date create at 2023/6/22 10:30
 */
@Slf4j
public class RpcServer {
    /**
     * 注册中心
     */
    private RpcRegistry registry;
    /**
     * 用于执行服务注册的线程池
     */
    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 10,
            1000, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public RpcServer() {}

    /**
     * 初始化rpc服务端
     *
     * @throws Exception
     */
    public void init() throws Exception {
        // 设置配置项
        RpcServerConfig rpcServerConfig = RpcConfigLoader.loadRpcServerConfig();
        RpcServerContext.setRpcServerConfig(rpcServerConfig);

        new ServerBootstrap()
                .group(new NioEventLoopGroup(), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_SNDBUF, 16 * 1024)
                .option(ChannelOption.SO_RCVBUF, 16 * 1024)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(new RpcTransferProtocolEncoder());
                        channel.pipeline().addLast(new RpcTransferProtocolDecoder());
                        channel.pipeline().addLast(new RpcServerHandler());
                    }
                })
                .bind(rpcServerConfig.getPort())
                .sync();

        RpcRegistryType registryType = rpcServerConfig.getCommonConfig().getRegistryType();
        if (registryType == RpcRegistryType.ZK) {
            // 如果注册中心的类型是zookeeper
            ZookeeperClient zookeeperClient = new ZookeeperClient(rpcServerConfig.getCommonConfig().getRegistryAddr());
            registry = new ZookeeperRegistry(zookeeperClient);
        } else {
            throw new ConfigErrorException("未知注册中心类型");
        }

        RpcSerializeType serializeType = rpcServerConfig.getCommonConfig().getSerializeType();
        if (serializeType.getCode() == RpcSerializeType.JDK.getCode()) {
            RpcServerContext.setSerializeFactory(new JdkRpcSerializeFactory());
        } else if (serializeType.getCode() == RpcSerializeType.FASTJSON.getCode()) {
            RpcServerContext.setSerializeFactory(new FastJsonRpcSerializeFactory());
        } else {
            throw new ConfigErrorException("暂时不支持的序列化类型");
        }
    }

    /**
     * 注册服务
     *
     * @param service
     */
    public void registerService(Object service) {
        // 将当前服务名称写入
        threadPoolExecutor.submit(() -> {
            // 首先将当前服务写入注册中心
            Class<?>[] interfaces = service.getClass().getInterfaces();
            if (interfaces.length != 1) {
                log.error("#{} implement too many interface!", service);
                return;
            }
            RpcServerConfig rpcServerConfig = RpcServerContext.getRpcServerConfig();
            Class<?> clazz = interfaces[0];
            String serviceName = clazz.getName();
            RegistryData registryData = new RegistryData();
            registryData.setIp(CommonUtils.getCurrentMachineIp());
            registryData.setServiceName(serviceName);
            registryData.setPort(rpcServerConfig.getPort());
            registryData.setApplicationName(rpcServerConfig.getCommonConfig().getApplicationName());
            registryData.getAttr().put(RpcRegistryDataAttrName.TYPE.getDescription(), "provider");
            registryData.getAttr().put(RpcRegistryDataAttrName.CREATE_TIME.getDescription(), System.currentTimeMillis());
            try {
                registry.register(registryData);
                // 将当前服务写入本地缓存中
                RpcServerContext.getRegistryDataCache().put(serviceName, registryData);
                RpcServerContext.getServiceProviderCache().put(serviceName, service);
            } catch (Exception e) {
                log.error("register service failure, service name is #{}, exception is #{}", service, e.getMessage());
            }
        });
    }
}