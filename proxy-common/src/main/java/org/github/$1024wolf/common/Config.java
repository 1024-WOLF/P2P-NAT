package org.github.$1024wolf.common;

import org.github.$1024wolf.common.util.LangUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Description:
 * 读取配置文件，默认的 config.properties 和自定义都支持
 * @author zwl
 * @version 1.0
 * @date 2021/2/21 23:40
 */
public class Config {

    /**
     * 类路径下，默认的配置文件名称
     *
     */
    private static final String DEFAULT_CONF = "config.properties";

    /**
     * 配置文件集合
     *
     */
    private static final Map<String, Config> instances = new ConcurrentHashMap<>();

    private final Properties configuration = new Properties();

    /**
     * 初始化，加载配置文件
     *
     */
    public Config(){
        loadConfig(DEFAULT_CONF);
    }

    /**
     * 初始化，加载指定的配置文件
     *
     * @param fileName 指定的配置文件，配置文件放置在类路径下
     */
    public Config(String fileName){
        loadConfig(fileName);
    }

    /**
     * 加载配置文件
     *
     * @param fileName 配置文件名称；默认是 {@link Config#DEFAULT_CONF}
     */
    private void loadConfig(String fileName){
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(fileName)) {
            configuration.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Config getInstance(){
        return getInstance(DEFAULT_CONF);
    }

    /**
     * 双重校验锁，获取配置
     *
     * @param fileName 配置文件名称
     * @return Config实例
     */
    public static Config getInstance(String fileName){
        Config config = instances.get(fileName);
        if (config == null){
            synchronized (instances){
                config = instances.get(fileName);
                if (config == null){
                    config = new Config(fileName);
                    instances.put(fileName,config);
                }
            }
        }
        return config;
    }

    /**
     * 获得配置项
     *
     * @param key 配置关键字
     *
     * @return 返回配置项；如果配置项不存在，返回 null
     */
    public String getStringValue(String key) {
        return configuration.getProperty(key);
    }

    public String getStringValue(String key, String defaultValue) {
        String value = this.getStringValue(key);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

    public int getIntValue(String key, int defaultValue) {
        return LangUtil.parseInt(configuration.getProperty(key), defaultValue);
    }

    public int getIntValue(String key) {
        return LangUtil.parseInt(configuration.getProperty(key));
    }

    public double getDoubleValue(String key, Double defaultValue) {
        return LangUtil.parseDouble(configuration.getProperty(key), defaultValue);
    }

    public double getDoubleValue(String key) {
        return LangUtil.parseDouble(configuration.getProperty(key));
    }

    public double getLongValue(String key, Long defaultValue) {
        return LangUtil.parseLong(configuration.getProperty(key), defaultValue);
    }

    public double getLongValue(String key) {
        return LangUtil.parseLong(configuration.getProperty(key));
    }

    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        return LangUtil.parseBoolean(configuration.getProperty(key), defaultValue);
    }

    public Boolean getBooleanValue(String key) {
        return LangUtil.parseBoolean(configuration.getProperty(key));
    }
}
