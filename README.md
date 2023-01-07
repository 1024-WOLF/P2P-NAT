# 功能特性

1. web控制台配置代理信息
2. 基于tcp协议的流量转发

> jdk8及以上

# 思维导图

[![faf2RA.png](https://z3.ax1x.com/2021/08/11/faf2RA.png)](https://imgtu.com/i/faf2RA)

[![fahQWd.png](https://z3.ax1x.com/2021/08/11/fahQWd.png)](https://imgtu.com/i/fahQWd)


# 功能迭代&优化

1. 代理客户端和代理服务端建立 cmdChannel 后，长时间（例如，30分钟）没有 userChannel 建立，那么就需要关闭 开放的 port，并且 close cmdChannel，节约资源

```
// 关闭 port
for cmdChannel : cmdChannelSet
    cmdChannel.close()
```

> 注意：此功能，可以使用配置文件的形式，配置是否开启此功能，默认是开启的

2. 代理客户端鉴权需要加密，下发 public_key

3. 支持 UPD 协议

4. 代理信息采用 sqlite 数据库管理

5. 采用 logback 打印日志 【完成】

