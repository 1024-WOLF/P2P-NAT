package com.github.tangmonkmeat.config;

import com.github.tangmonkmeat.common.Config;
import com.github.tangmonkmeat.common.util.JsonUtil;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Description:
 * 代理服务器配置
 * @author zwl
 * @version 1.0
 * @date 2021/2/27 下午9:33
 */
public class ProxyConfig implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(ProxyConfig.class);

    /**
     * 代理客户端配置文件的路径，默认的配置文件在 用户目录的 .proxy-server/ 目录下，名称为 config.json
     */
    public static final String CONFIG_FILE;

    // 初始化配置文件路径
    static {
        String configDir = System.getProperty("user.home") + "/" + ".proxy-server/";
        File file = new File(configDir);
        if (!file.exists()){
            file.mkdir();
        }
        CONFIG_FILE = configDir + "config.json";
    }

    /**
     * 代理服务器绑定的ip
     */
    private String serverHost;

    /**
     * 代理服务器绑定的端口
     */
    private Integer serverPort;

    /**
     * web控制台绑定的ip
     */
    private String configServerHost;

    /**
     * web控制台绑定的端口
     */
    private Integer configServerPort;

    /**
     * web控制台的登录账号
     */
    private String configServerUserName;

    /**
     * web控制台的登录密码
     */
    private String configServerPassword;

    /** 代理客户端，支持多个客户端 */
    private List<Client> clients;

    /** 更新配置后保证在其他线程即时生效 */
    public static volatile ProxyConfig instance = new ProxyConfig();

    /** 代理服务器为各个代理客户端（key）开启对应的端口列表（value） */
    private volatile Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>();

    /** 代理服务器上的每个对外端口（key）对应的代理客户端背后的真实服务器信息（value） */
    private volatile Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>();

    /** 配置变化监听器 */
    private List<ConfigChangedListener> configChangedListeners = new ArrayList<ConfigChangedListener>();

    private ProxyConfig(){
        // 代理服务器主机和端口配置初始化
        this.serverPort = Config.getInstance().getIntValue("server.port");
        this.serverHost = Config.getInstance().getStringValue("server.host", "0.0.0.0");

        // 配置服务器主机和端口配置初始化
        this.configServerPort = Config.getInstance().getIntValue("config.server.port");
        this.configServerHost = Config.getInstance().getStringValue("config.server.host", "0.0.0.0");

        // 配置服务器管理员登录认证信息
        this.configServerUserName = Config.getInstance().getStringValue("config.admin.username");
        this.configServerPassword = Config.getInstance().getStringValue("config.admin.password");

        logger.info(
                "config init serverHost {}, serverPort {}, configServerHost {}, configServerPort {}, configServerUserName {}, configServerPassword {}",
                serverHost, serverPort, configServerHost, configServerPort, configServerUserName, configServerPassword);

        update(null);
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public String getConfigServerHost() {
        return configServerHost;
    }

    public void setConfigServerHost(String configServerHost) {
        this.configServerHost = configServerHost;
    }

    public Integer getConfigServerPort() {
        return configServerPort;
    }

    public void setConfigServerPort(Integer configServerPort) {
        this.configServerPort = configServerPort;
    }

    public String getConfigServerUserName() {
        return configServerUserName;
    }

    public void setConfigServerUserName(String configServerUserName) {
        this.configServerUserName = configServerUserName;
    }

    public String getConfigServerPassword() {
        return configServerPassword;
    }

    public void setConfigServerPassword(String configServerPassword) {
        this.configServerPassword = configServerPassword;
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public Map<String, List<Integer>> getClientInetPortMapping() {
        return clientInetPortMapping;
    }

    public void setClientInetPortMapping(Map<String, List<Integer>> clientInetPortMapping) {
        this.clientInetPortMapping = clientInetPortMapping;
    }

    public Map<Integer, String> getInetPortLanInfoMapping() {
        return inetPortLanInfoMapping;
    }

    public void setInetPortLanInfoMapping(Map<Integer, String> inetPortLanInfoMapping) {
        this.inetPortLanInfoMapping = inetPortLanInfoMapping;
    }

    public List<ConfigChangedListener> getConfigChangedListeners() {
        return configChangedListeners;
    }

    public void setConfigChangedListeners(List<ConfigChangedListener> configChangedListeners) {
        this.configChangedListeners = configChangedListeners;
    }

    /**
     * 重新加载配置文件信息，优先加载顺序 json > 配置文件
     *
     * 1 如果 json == null，配置文件存在，加载配置文件
     * 2 如果 配置文件不存在，json != null，解析json
     * 3 如果 json == null，且配置文件不存在，或者配置文件内容为空，抛异常
     *
     * @param proxyMappingConfigJson json信息
     */
    public void update(String proxyMappingConfigJson){
        File file = new File(CONFIG_FILE);
        InputStream in = null;
        try {
            // 如果json 为 null，且配置文件不存在
            if (proxyMappingConfigJson == null && file.exists()) {
                in = new FileInputStream(file);
                byte[] buf = new byte[1024];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int readIndex;
                while ((readIndex = in.read(buf)) != -1) {
                    out.write(buf, 0, readIndex);
                }

                in.close();
                proxyMappingConfigJson = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(in != null){
                    in.close();
                }
            } catch (IOException e) {
                logger.error("close inputStream fail",e);
            }
        }

        // json to object
        List<Client> clients = JsonUtil.json2Object(proxyMappingConfigJson, new TypeToken<List<Client>>() {
        });
        if (clients == null) {
            clients = new ArrayList<Client>();
        }

        // clientKey : proxyServer_port列表
        Map<String, List<Integer>> clientInetPortMapping = new HashMap<String, List<Integer>>(3);
        // proxyServer_port : client_ip:client_port
        Map<Integer, String> inetPortLanInfoMapping = new HashMap<Integer, String>(3);

        // 构造端口映射关系
        for (Client client : clients) {
            String clientKey = client.getClientKey();
            if (clientInetPortMapping.containsKey(clientKey)) {
                throw new IllegalArgumentException("密钥同时作为客户端标识，不能重复： " + clientKey);
            }
            List<ClientProxyMapping> mappings = client.getProxyMappings();
            List<Integer> ports = new ArrayList<Integer>();
            clientInetPortMapping.put(clientKey, ports);
            for (ClientProxyMapping mapping : mappings) {
                Integer port = mapping.getInetPort();
                ports.add(port);
                if (inetPortLanInfoMapping.containsKey(port)) {
                    throw new IllegalArgumentException("一个公网端口只能映射一个后端信息，不能重复: " + port);
                }

                inetPortLanInfoMapping.put(port, mapping.getLan());
            }
        }

        // 替换之前的配置关系
        this.clientInetPortMapping = clientInetPortMapping;
        this.inetPortLanInfoMapping = inetPortLanInfoMapping;
        this.clients = clients;

        // 更新配置文件
        if (proxyMappingConfigJson != null) {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                out.write(proxyMappingConfigJson.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    assert out != null;
                    out.close();
                } catch (IOException e) {
                    logger.error("close FileOutputStream fail",e);
                }
            }
        }

        // 配置更新通知
        notifyConfigChangedListeners();
    }

    /**
     * 添加配置变化监听器
     */
    public void addConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.add(configChangedListener);
    }

    /**
     * 移除配置变化监听器
     */
    public void removeConfigChangedListener(ConfigChangedListener configChangedListener) {
        configChangedListeners.remove(configChangedListener);
    }

    /**
     * 获取代理客户端对应的代理服务器端口
     */
    public List<Integer> getClientInetPorts(String clientKey) {
        return clientInetPortMapping.get(clientKey);
    }

    /**
     * 获取所有的clientKey
     */
    public Set<String> getClientKeySet() {
        return clientInetPortMapping.keySet();
    }

    /**
     * 根据代理服务器端口获取后端服务器代理信息
     */
    public String getLanInfo(Integer port) {
        return inetPortLanInfoMapping.get(port);
    }

    /**
     * 返回需要绑定在代理服务器的端口（用于用户请求）
     */
    public List<Integer> getUserPorts() {
        return new ArrayList<Integer>(inetPortLanInfoMapping.keySet());
    }

    /**
     * 配置更新通知
     */
    public void notifyConfigChangedListeners(){
        List<ConfigChangedListener> changedListeners = new ArrayList<ConfigChangedListener>(configChangedListeners);
        for (ConfigChangedListener changedListener : changedListeners) {
            changedListener.onChanged();
        }
    }

    /**
     * 配置更新回调
     */
    public interface ConfigChangedListener {
        void onChanged();
    }

    /**
     * 代理客户端
     */
    public static class Client implements Serializable{
        /**
         * 代理客户端的备注名称
         */
        private String name;

        /**
         * 代理客户端的唯一标识
         */
        private String clientKey;

        /**
         * 代理客户端的状态；
         * 1-在线
         * 0-离线
         */
        private Integer status;

        /** 代理客户端与其后面的真实服务器映射关系 */
        private List<ClientProxyMapping> proxyMappings;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getClientKey() {
            return clientKey;
        }

        public void setClientKey(String clientKey) {
            this.clientKey = clientKey;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public List<ClientProxyMapping> getProxyMappings() {
            return proxyMappings;
        }

        public void setProxyMappings(List<ClientProxyMapping> proxyMappings) {
            this.proxyMappings = proxyMappings;
        }
    }

    /**
     * 代理客户端与其后面真实服务器映射关系
     */
    public static class ClientProxyMapping implements Serializable{
        /** 代理服务器端口 */
        private Integer inetPort;

        /** 需要代理的网络信息（代理客户端能够访问），格式 192.168.1.99:80 (必须带端口) */
        private String lan;

        /** 备注名称 */
        private String name;

        public Integer getInetPort() {
            return inetPort;
        }

        public void setInetPort(Integer inetPort) {
            this.inetPort = inetPort;
        }

        public String getLan() {
            return lan;
        }

        public void setLan(String lan) {
            this.lan = lan;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

}
